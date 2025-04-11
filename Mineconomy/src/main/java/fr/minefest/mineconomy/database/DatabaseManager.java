package fr.minefest.mineconomy.database;

import fr.minefest.mineconomy.Mineconomy;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {
    private final Mineconomy plugin;
    private final ExecutorService executor;
    private Connection connection;
    private final String dbUrl;

    public DatabaseManager(Mineconomy plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor();
        
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        this.dbUrl = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            openConnection();
            createTables();
            plugin.getLogger().info("Base de données initialisée avec succès.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Le driver SQLite n'a pas été trouvé: " + e.getMessage());
            throw new RuntimeException("Erreur d'initialisation de la base de données", e);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la création des tables: " + e.getMessage());
            throw new RuntimeException("Erreur de création des tables", e);
        }
    }

    private void openConnection() throws SQLException {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            
            connection = DriverManager.getConnection(dbUrl);
            connection.setAutoCommit(true);
            
            // Activation des clés étrangères pour SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de l'ouverture de la connexion à la base de données: " + e.getMessage());
            throw e;
        }
    }

    private void createTables() throws SQLException {
        String bankTypes = "CREATE TABLE IF NOT EXISTS bank_types (" +
                "bank_name TEXT PRIMARY KEY, " +
                "currency_symbol TEXT NOT NULL, " +
                "default_bank BOOLEAN NOT NULL DEFAULT 0)";

        String teamBanks = "CREATE TABLE IF NOT EXISTS team_banks (" +
                "team_name TEXT NOT NULL, " +
                "bank_name TEXT NOT NULL, " +
                "balance REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (team_name, bank_name), " +
                "FOREIGN KEY (bank_name) REFERENCES bank_types(bank_name) ON DELETE CASCADE)";

        String globalItemSales = "CREATE TABLE IF NOT EXISTS global_item_sales (" +
                "item_id TEXT PRIMARY KEY, " +
                "total_sold INTEGER NOT NULL DEFAULT 0)";

        String dailyTeamLimits = "CREATE TABLE IF NOT EXISTS daily_team_limits (" +
                "sale_date TEXT NOT NULL, " +
                "team_name TEXT NOT NULL, " +
                "item_id TEXT NOT NULL, " +
                "quantity_sold INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (sale_date, team_name, item_id))";

        String playerContributions = "CREATE TABLE IF NOT EXISTS player_contributions (" +
                "player_uuid TEXT NOT NULL, " +
                "team_name TEXT NOT NULL, " +
                "bank_name TEXT NOT NULL, " +
                "amount_contributed REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (player_uuid, team_name, bank_name), " +
                "FOREIGN KEY (team_name, bank_name) REFERENCES team_banks(team_name, bank_name) ON DELETE CASCADE)";

        // Créer les index pour optimiser les requêtes
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_team_banks_team ON team_banks(team_name)",
                "CREATE INDEX IF NOT EXISTS idx_team_banks_bank ON team_banks(bank_name)",
                "CREATE INDEX IF NOT EXISTS idx_daily_team_limits_date ON daily_team_limits(sale_date)",
                "CREATE INDEX IF NOT EXISTS idx_daily_team_limits_team ON daily_team_limits(team_name)",
                "CREATE INDEX IF NOT EXISTS idx_player_contributions_player ON player_contributions(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_player_contributions_team ON player_contributions(team_name)",
                "CREATE INDEX IF NOT EXISTS idx_player_contributions_bank ON player_contributions(bank_name)"
        };

        try (Statement stmt = connection.createStatement()) {
            // Créer les tables
            stmt.execute(bankTypes);
            stmt.execute(teamBanks);
            stmt.execute(globalItemSales);
            stmt.execute(dailyTeamLimits);
            stmt.execute(playerContributions);
            
            // Créer les index
            for (String index : indexes) {
                stmt.execute(index);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        openConnection();
        return connection;
    }

    public void close() {
        executor.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la fermeture de la connexion: " + e.getMessage(), e);
        }
    }

    // Méthodes utilitaires pour les transactions

    public void executeTransaction(Consumer<Connection> transaction) {
        Connection conn = null;
        try {
            conn = getConnection();
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            try {
                transaction.accept(conn);
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors du rollback de la transaction: " + ex.getMessage(), ex);
                }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur SQL lors de l'exécution de la transaction: " + e.getMessage(), e);
            throw new RuntimeException("Erreur de transaction", e);
        }
    }

    public CompletableFuture<Void> executeTransactionAsync(Consumer<Connection> transaction) {
        return CompletableFuture.runAsync(() -> executeTransaction(transaction), executor)
                .exceptionally(e -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution asynchrone: " + e.getMessage(), e);
                    });
                    return null;
                });
    }

    // Méthodes utilitaires pour préparer et exécuter des requêtes

    public PreparedStatement prepareStatement(Connection conn, String sql) throws SQLException {
        try {
            return conn.prepareStatement(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la préparation de la requête: " + sql, e);
            throw e;
        }
    }

    public void executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = prepareStatement(conn, sql)) {
            
            setParameters(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution de la mise à jour: " + sql, e);
            throw new RuntimeException("Erreur d'exécution SQL", e);
        }
    }

    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = prepareStatement(conn, sql)) {
            
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return handler.handle(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution de la requête: " + sql, e);
            throw new RuntimeException("Erreur d'exécution SQL", e);
        }
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof String) {
                ps.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                ps.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                ps.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                ps.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof LocalDate) {
                ps.setString(i + 1, param.toString());
            } else if (param == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }

    // Interface fonctionnelle pour traiter les ResultSet
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}
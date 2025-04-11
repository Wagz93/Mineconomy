package fr.minefest.mineconomy.bank;

import fr.minefest.api.MinefestAPI;
import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.database.DatabaseManager;
import fr.minefest.mineconomy.util.MessageUtil;
import fr.minefest.models.Team;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class BankManager {
    private final Mineconomy plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, String> currencySymbols = new HashMap<>();
    private String defaultBankName;

    public BankManager(Mineconomy plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void initialize() {
        defaultBankName = plugin.getConfigManager().getConfig(fr.minefest.mineconomy.config.ConfigManager.ConfigType.CONFIG)
                .getString("bank.default_name", "Minefest");

        String defaultCurrencySymbol = plugin.getConfigManager().getConfig(fr.minefest.mineconomy.config.ConfigManager.ConfigType.CONFIG)
                .getString("bank.default_currency_symbol", "€");

        createDefaultBankType(defaultBankName, defaultCurrencySymbol, true);
        loadCurrencySymbols();
        syncTeamBanks();
    }

    private void loadCurrencySymbols() {
        String sql = "SELECT bank_name, currency_symbol FROM bank_types";

        databaseManager.executeQuery(sql, rs -> {
            currencySymbols.clear();
            while (rs.next()) {
                currencySymbols.put(rs.getString("bank_name"), rs.getString("currency_symbol"));
            }
            return null;
        });
    }

    private void createDefaultBankType(String bankName, String currencySymbol, boolean isDefault) {
        String sql = "INSERT OR IGNORE INTO bank_types (bank_name, currency_symbol, default_bank) VALUES (?, ?, ?)";

        try {
            databaseManager.executeUpdate(sql, bankName, currencySymbol, isDefault);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du type de banque par défaut", e);
        }
    }

    public void syncTeamBanks() {
        try {
            MinefestAPI api = plugin.getMinefestAPI();
            if (api == null) {
                plugin.getLogger().severe("MinefestAPI est null, impossible de synchroniser les banques d'équipe.");
                return;
            }

            Set<String> teamsFromApi = new HashSet<>(api.getTeamsList());
            Set<String> teamsInDatabase = getTeamsFromDatabase();

            for (String teamName : teamsFromApi) {
                if (!teamsInDatabase.contains(teamName)) {
                    createTeamBank(teamName, defaultBankName);
                }
            }

            plugin.getLogger().info("Synchronisation des banques d'équipe terminée.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la synchronisation des banques d'équipe", e);
        }
    }

    private Set<String> getTeamsFromDatabase() {
        String sql = "SELECT DISTINCT team_name FROM team_banks";

        return databaseManager.executeQuery(sql, rs -> {
            Set<String> teams = new HashSet<>();
            while (rs.next()) {
                teams.add(rs.getString("team_name"));
            }
            return teams;
        });
    }

    public boolean createTeamBank(String teamName, String bankName) {
        String sql = "INSERT OR IGNORE INTO team_banks (team_name, bank_name, balance) VALUES (?, ?, 0)";

        try {
            databaseManager.executeUpdate(sql, teamName, bankName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création de la banque d'équipe: " + teamName + ", " + bankName, e);
            return false;
        }
    }

    public boolean createBankType(String bankName, String currencySymbol, boolean isDefault) {
        String sql = "INSERT INTO bank_types (bank_name, currency_symbol, default_bank) VALUES (?, ?, ?)";

        try {
            databaseManager.executeTransaction(conn -> {
                try {
                    if (isDefault) {
                        try (PreparedStatement ps = conn.prepareStatement("UPDATE bank_types SET default_bank = 0")) {
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            throw new RuntimeException("Erreur lors de la mise à jour des banques par défaut", e);
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, bankName);
                        ps.setString(2, currencySymbol);
                        ps.setBoolean(3, isDefault);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException("Erreur lors de l'insertion de la nouvelle banque", e);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Erreur inattendue lors de la création de la banque", e);
                }
            });

            currencySymbols.put(bankName, currencySymbol);

            if (isDefault) {
                defaultBankName = bankName;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du type de banque: " + bankName, e);
            return false;
        }
    }

    public double getBankBalance(String teamName, String bankName) {
        String sql = "SELECT balance FROM team_banks WHERE team_name = ? AND bank_name = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
                return 0.0;
            }, teamName, bankName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération du solde: " + teamName + ", " + bankName, e);
            return 0.0;
        }
    }

    public boolean addBankBalance(String teamName, String bankName, double amount) {
        if (amount <= 0) return false;

        String sql = "INSERT INTO team_banks (team_name, bank_name, balance) VALUES (?, ?, ?) "
                + "ON CONFLICT(team_name, bank_name) DO UPDATE SET balance = balance + ?";

        try {
            databaseManager.executeUpdate(sql, teamName, bankName, amount, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'ajout au solde: " + teamName + ", " + bankName + ", " + amount, e);
            return false;
        }
    }

    public boolean removeBankBalance(String teamName, String bankName, double amount) {
        if (amount <= 0) return false;

        String sql = "UPDATE team_banks SET balance = CASE WHEN balance >= ? THEN balance - ? ELSE balance END "
                + "WHERE team_name = ? AND bank_name = ?";

        try {
            databaseManager.executeUpdate(sql, amount, amount, teamName, bankName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du retrait du solde: " + teamName + ", " + bankName + ", " + amount, e);
            return false;
        }
    }

    public boolean setBankBalance(String teamName, String bankName, double amount) {
        if (amount < 0) return false;

        String sql = "INSERT INTO team_banks (team_name, bank_name, balance) VALUES (?, ?, ?) "
                + "ON CONFLICT(team_name, bank_name) DO UPDATE SET balance = ?";

        try {
            databaseManager.executeUpdate(sql, teamName, bankName, amount, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la définition du solde: " + teamName + ", " + bankName + ", " + amount, e);
            return false;
        }
    }

    public boolean updatePlayerContribution(UUID playerUuid, String teamName, String bankName, double amount) {
        if (amount <= 0) return false;

        String sql = "INSERT INTO player_contributions (player_uuid, team_name, bank_name, amount_contributed) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, team_name, bank_name) "
                + "DO UPDATE SET amount_contributed = amount_contributed + ?";

        try {
            databaseManager.executeUpdate(sql, playerUuid.toString(), teamName, bankName, amount, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour des contributions: "
                    + playerUuid + ", " + teamName + ", " + bankName + ", " + amount, e);
            return false;
        }
    }

    public void resetContributions(String bankName) {
        try {
            if (bankName == null || bankName.isEmpty()) {
                databaseManager.executeUpdate("DELETE FROM player_contributions");
            } else {
                databaseManager.executeUpdate("DELETE FROM player_contributions WHERE bank_name = ?", bankName);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la réinitialisation des contributions", e);
        }
    }

    public double getPlayerContribution(UUID playerUuid, String teamName, String bankName) {
        String sql = "SELECT amount_contributed FROM player_contributions "
                + "WHERE player_uuid = ? AND team_name = ? AND bank_name = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                if (rs.next()) {
                    return rs.getDouble("amount_contributed");
                }
                return 0.0;
            }, playerUuid.toString(), teamName, bankName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des contributions: "
                    + playerUuid + ", " + teamName + ", " + bankName, e);
            return 0.0;
        }
    }

    public Map<String, Double> getTeamBankAccounts(String teamName) {
        String sql = "SELECT bank_name, balance FROM team_banks WHERE team_name = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                Map<String, Double> accounts = new HashMap<>();
                while (rs.next()) {
                    accounts.put(rs.getString("bank_name"), rs.getDouble("balance"));
                }
                return accounts;
            }, teamName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des comptes de l'équipe: " + teamName, e);
            return Collections.emptyMap();
        }
    }

    public List<String> getAllBankTypes() {
        String sql = "SELECT bank_name FROM bank_types ORDER BY default_bank DESC, bank_name ASC";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                List<String> types = new ArrayList<>();
                while (rs.next()) {
                    types.add(rs.getString("bank_name"));
                }
                return types;
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des types de banque", e);
            return Collections.emptyList();
        }
    }

    public String getBankCurrency(String bankName) {
        return currencySymbols.getOrDefault(bankName, "€");
    }

    public String getDefaultBankName() {
        return defaultBankName;
    }

    public TopContributor getTopContributor(String teamName, String bankName) {
        String sql = "SELECT player_uuid, amount_contributed FROM player_contributions " +
                "WHERE team_name = ? AND bank_name = ? ORDER BY amount_contributed DESC LIMIT 1";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                if (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    double amount = rs.getDouble("amount_contributed");
                    return new TopContributor(UUID.fromString(playerUuid), amount);
                }
                return null;
            }, teamName, bankName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération du meilleur contributeur", e);
            return null;
        }
    }

    public int getTeamRank(String teamName, String bankName) {
        String sql = "SELECT team_name, balance, " +
                "(SELECT COUNT(*) + 1 FROM team_banks t2 WHERE t2.bank_name = ? AND t2.balance > t1.balance) AS rank " +
                "FROM team_banks t1 WHERE t1.team_name = ? AND t1.bank_name = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                if (rs.next()) {
                    return rs.getInt("rank");
                }
                return 0;
            }, bankName, teamName, bankName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération du rang de l'équipe", e);
            return 0;
        }
    }

    public List<TeamBalance> getTopTeams(String bankName, int limit) {
        String sql = "SELECT team_name, balance FROM team_banks WHERE bank_name = ? ORDER BY balance DESC LIMIT ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                List<TeamBalance> topTeams = new ArrayList<>();
                while (rs.next()) {
                    topTeams.add(new TeamBalance(rs.getString("team_name"), rs.getDouble("balance")));
                }
                return topTeams;
            }, bankName, limit);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des meilleures équipes", e);
            return Collections.emptyList();
        }
    }

    public static class TopContributor {
        private final UUID playerUuid;
        private final double amount;

        public TopContributor(UUID playerUuid, double amount) {
            this.playerUuid = playerUuid;
            this.amount = amount;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public double getAmount() {
            return amount;
        }
    }

    public static class TeamBalance {
        private final String teamName;
        private final double balance;

        public TeamBalance(String teamName, double balance) {
            this.teamName = teamName;
            this.balance = balance;
        }

        public String getTeamName() {
            return teamName;
        }

        public double getBalance() {
            return balance;
        }
    }

    public String getPlayerTeam(Player player) {
        try {
            Team team = plugin.getMinefestAPI().getPlayerTeam(player);
            return team != null ? team.getName() : null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération de l'équipe du joueur", e);
            return null;
        }
    }

    public String getPlayerTeam(OfflinePlayer player) {
        try {
            Team team = plugin.getMinefestAPI().getPlayerTeam(player);
            return team != null ? team.getName() : null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération de l'équipe du joueur", e);
            return null;
        }
    }

    public String getPlayerTeam(UUID playerUuid) {
        try {
            Team team = plugin.getMinefestAPI().getPlayerTeam(playerUuid);
            return team != null ? team.getName() : null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération de l'équipe du joueur", e);
            return null;
        }
    }
}
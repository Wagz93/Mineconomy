package fr.minefest.mineconomy;

import fr.minefest.api.MinefestAPI;
import fr.minefest.mineconomy.api.MinefestShopAPI;
import fr.minefest.mineconomy.bank.BankManager;
import fr.minefest.mineconomy.command.CommandRegistry;
import fr.minefest.mineconomy.command.ConsoleCommandManager;
import fr.minefest.mineconomy.config.ConfigManager;
import fr.minefest.mineconomy.database.DatabaseManager;
import fr.minefest.mineconomy.placeholder.PlaceholderManager;
import fr.minefest.mineconomy.price.DynamicPriceManager;
import fr.minefest.mineconomy.shop.ShopManager;
import fr.minefest.mineconomy.util.MessageUtil;
import fr.minefest.mineconomy.util.SplashManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class Mineconomy extends JavaPlugin {
    private static Mineconomy instance;
    private MinefestAPI minefestAPI;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private BankManager bankManager;
    private ShopManager shopManager;
    private DynamicPriceManager priceManager;
    private CommandRegistry commandRegistry;
    private PlaceholderManager placeholderManager;
    private MinefestShopAPI api;
    private boolean apiHooked = false;

    @Override
    public void onEnable() {
        instance = this;
        
        if (!hookMinefestAPI()) {
            getLogger().severe("Impossible de se connecter à MinefestAPI. Désactivation du plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        try {
            initializeManagers();
            registerCommands();
            registerPlaceholders();
            
            // Créer et exposer l'API
            api = new MinefestShopAPI(this);
            
            // Afficher le splash dans la console
            SplashManager.showSplash(this);
            
            getLogger().info("Mineconomy a été activé avec succès.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Erreur critique lors de l'initialisation du plugin:", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Mineconomy a été désactivé.");
    }

    private boolean hookMinefestAPI() {
        try {
            Plugin minefestPlugin = Bukkit.getPluginManager().getPlugin("Minefest");
            if (minefestPlugin == null) {
                getLogger().severe("Le plugin Minefest n'a pas été trouvé!");
                return false;
            }

            if (!(minefestPlugin instanceof fr.minefest.Minefest)) {
                getLogger().severe("Le plugin trouvé n'est pas le bon type de Minefest!");
                return false;
            }

            fr.minefest.Minefest minefest = (fr.minefest.Minefest) minefestPlugin;
            minefestAPI = minefest.getApi();

            if (minefestAPI == null) {
                getLogger().severe("Impossible d'obtenir l'API Minefest!");
                return false;
            }

            apiHooked = true;
            getLogger().info("MinefestAPI connectée avec succès.");
            return true;
        } catch (Exception e) {
            getLogger().severe("Erreur lors de la connexion à MinefestAPI: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void initializeManagers() {
        try {
            configManager = new ConfigManager(this);
            configManager.loadConfigurations();
            
            MessageUtil.initialize(configManager);
            
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            
            bankManager = new BankManager(this, databaseManager);
            bankManager.initialize();
            
            priceManager = new DynamicPriceManager(this, databaseManager, configManager);
            priceManager.initialize();
            
            shopManager = new ShopManager(this, databaseManager, bankManager, priceManager, configManager);
            shopManager.initialize();
        } catch (Exception e) {
            getLogger().severe("Erreur lors de l'initialisation des managers: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur critique d'initialisation", e);
        }
    }

    private void registerCommands() {
        commandRegistry = new CommandRegistry(this);
        commandRegistry.registerCommands();
        
        // Enregistrement de la commande console
        getCommand("mfshop").setExecutor(new ConsoleCommandManager(this));
        getCommand("mfshop").setTabCompleter(new ConsoleCommandManager(this));
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderManager = new PlaceholderManager(this);
            placeholderManager.register();
            getLogger().info("PlaceholderAPI intégré avec succès.");
        } else {
            getLogger().warning("PlaceholderAPI non trouvé. Les placeholders ne seront pas disponibles.");
        }
    }

    public void reload() {
        try {
            configManager.loadConfigurations();
            MessageUtil.initialize(configManager);
            priceManager.reload();
            shopManager.reload();
            getLogger().info("Mineconomy a été rechargé avec succès.");
        } catch (Exception e) {
            getLogger().severe("Erreur lors du rechargement: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Mineconomy getInstance() {
        return instance;
    }

    public MinefestAPI getMinefestAPI() {
        return minefestAPI;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public DynamicPriceManager getPriceManager() {
        return priceManager;
    }
    
    public MinefestShopAPI getApi() {
        return api;
    }

    public boolean isApiHooked() {
        return apiHooked;
    }
}
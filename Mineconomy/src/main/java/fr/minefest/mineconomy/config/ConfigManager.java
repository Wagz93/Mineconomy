package fr.minefest.mineconomy.config;

import fr.minefest.mineconomy.Mineconomy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private final Mineconomy plugin;
    private final Map<ConfigType, FileConfiguration> configs = new HashMap<>();
    private final Map<Integer, FileConfiguration> dailyShopConfigs = new HashMap<>();
    
    private int currentDayNumber = -1;
    private boolean dailyShopEnabled = true;

    public ConfigManager(Mineconomy plugin) {
        this.plugin = plugin;
    }

    public void loadConfigurations() {
        // Charger les configurations principales
        for (ConfigType type : ConfigType.values()) {
            loadConfig(type);
        }
        
        // Créer le dossier pour les shops journaliers s'il n'existe pas
        File dayShopFolder = new File(plugin.getDataFolder(), "day_shop");
        if (!dayShopFolder.exists()) {
            dayShopFolder.mkdirs();
        }
        
        // Charger le shop du jour actuel
        loadCurrentDayShop();
    }
    
    private void loadConfig(ConfigType type) {
        File configFile = new File(plugin.getDataFolder(), type.getFileName());
        
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource(type.getFileName(), false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Vérifier que la config est à jour avec les valeurs par défaut
        InputStream defaultConfigStream = plugin.getResource(type.getFileName());
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            
            // Ajouter les clés manquantes
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                }
            }
            
            // Sauvegarder si des modifications ont été faites
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder la configuration " 
                        + type.getFileName(), e);
            }
        }
        
        configs.put(type, config);
        plugin.getLogger().info("Configuration " + type.getFileName() + " chargée.");
    }
    
    public void loadCurrentDayShop() {
        // Déterminer le jour actuel (1-7)
        int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue(); // 1 = Lundi, 7 = Dimanche
        
        // Charger le fichier de shop correspondant
        loadDayShop(dayOfWeek);
        
        currentDayNumber = dayOfWeek;
    }
    
    public void loadDayShop(int dayNumber) {
        if (dayNumber < 1 || dayNumber > 7) {
            plugin.getLogger().warning("Jour invalide spécifié: " + dayNumber + ". Doit être entre 1 et 7.");
            dayNumber = 1; // Fallback au jour 1
        }
        
        String fileName = "day_" + dayNumber + ".yml";
        File dayConfigFile = new File(plugin.getDataFolder() + "/day_shop", fileName);
        
        // Vérifier si le fichier existe, sinon créer un fichier par défaut
        if (!dayConfigFile.exists()) {
            try {
                // Essayer de copier depuis les ressources
                InputStream defaultConfigStream = plugin.getResource("day_shop/" + fileName);
                if (defaultConfigStream != null) {
                    Files.copy(defaultConfigStream, dayConfigFile.toPath());
                } else {
                    // Ou créer un fichier par défaut
                    createDefaultDayShopConfig(dayConfigFile);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de créer le fichier de shop du jour " 
                        + fileName, e);
                dailyShopEnabled = false;
                return;
            }
        }
        
        // Charger la configuration
        try {
            FileConfiguration dayConfig = YamlConfiguration.loadConfiguration(dayConfigFile);
            dailyShopConfigs.put(dayNumber, dayConfig);
            plugin.getLogger().info("Shop du jour " + fileName + " chargé.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement du shop du jour " 
                    + fileName, e);
            dailyShopEnabled = false;
        }
    }
    
    private void createDefaultDayShopConfig(File file) throws IOException {
        Path filePath = Paths.get(file.toURI());
        
        String defaultContent = "# Configuration pour le Shop du Jour\n" +
                "items:\n" +
                "  DIAMOND:\n" +
                "    price: 150.0\n" +
                "    team_sell_limit: 64\n" +
                "    gui_slot: 10\n" +
                "  IRON_INGOT:\n" +
                "    price: 10.0\n" +
                "    team_sell_limit: 512\n" +
                "    gui_slot: 12\n" +
                "  GOLD_INGOT:\n" +
                "    price: 25.0\n" +
                "    team_sell_limit: 256\n" +
                "    gui_slot: 14\n";
        
        Files.write(filePath, defaultContent.getBytes(StandardCharsets.UTF_8));
    }
    
    public FileConfiguration getConfig(ConfigType type) {
        return configs.getOrDefault(type, null);
    }
    
    public FileConfiguration getDayShopConfig(int day) {
        return dailyShopConfigs.getOrDefault(day, null);
    }
    
    public FileConfiguration getCurrentDayShopConfig() {
        return dailyShopConfigs.getOrDefault(currentDayNumber, null);
    }
    
    public int getCurrentDayNumber() {
        return currentDayNumber;
    }
    
    public boolean isDailyShopEnabled() {
        return dailyShopEnabled;
    }
    
    public void setCurrentDayNumber(int dayNumber) {
        this.currentDayNumber = dayNumber;
        loadDayShop(dayNumber);
    }
    
    public ConfigurationSection getShopConfig() {
        FileConfiguration config = getConfig(ConfigType.SHOP_DEFAULT);
        if (config == null) {
            return null;
        }
        return config.getConfigurationSection("items");
    }
    
    public ConfigurationSection getDynamicPricingConfig() {
        FileConfiguration config = getConfig(ConfigType.CONFIG);
        if (config == null) {
            return null;
        }
        return config.getConfigurationSection("dynamic_pricing");
    }
    
    public enum ConfigType {
        CONFIG("config.yml"),
        MESSAGES("messages.yml"),
        SHOP_DEFAULT("shop_default.yml");
        
        private final String fileName;
        
        ConfigType(String fileName) {
            this.fileName = fileName;
        }
        
        public String getFileName() {
            return fileName;
        }
    }
}
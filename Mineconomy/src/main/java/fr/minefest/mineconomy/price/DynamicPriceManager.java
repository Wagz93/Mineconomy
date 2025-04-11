package fr.minefest.mineconomy.price;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.config.ConfigManager;
import fr.minefest.mineconomy.database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DynamicPriceManager {
    private final Mineconomy plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Map<String, PricingData> pricingData = new ConcurrentHashMap<>();
    private final Map<String, Integer> itemSoldCache = new ConcurrentHashMap<>();

    public DynamicPriceManager(Mineconomy plugin, DatabaseManager databaseManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    public void initialize() {
        loadPricingConfig();
        loadSoldItems();
    }

    public void reload() {
        pricingData.clear();
        itemSoldCache.clear();
        initialize();
    }

    private void loadPricingConfig() {
        ConfigurationSection dynamicSection = configManager.getDynamicPricingConfig();
        if (dynamicSection == null) {
            plugin.getLogger().warning("Section de prix dynamiques non trouvée dans la configuration.");
            return;
        }

        for (String itemId : dynamicSection.getKeys(false)) {
            try {
                ConfigurationSection itemSection = dynamicSection.getConfigurationSection(itemId);
                if (itemSection == null) continue;

                double minimumMultiplier = itemSection.getDouble("minimum_multiplier", 0.5);

                // Essayer de lire les paliers comme une liste
                List<Map<?, ?>> tiersList = itemSection.getMapList("tiers");
                PricingData data = new PricingData(minimumMultiplier);

                if (!tiersList.isEmpty()) {
                    // Format de liste trouvé
                    for (Map<?, ?> tierMap : tiersList) {
                        int soldThreshold = ((Number) tierMap.get("sold_threshold")).intValue();
                        double multiplier = ((Number) tierMap.get("price_multiplier")).doubleValue();
                        data.addTier(new PriceTier(soldThreshold, multiplier));
                    }

                    data.sortTiers();
                    pricingData.put(itemId, data);
                    plugin.getLogger().info("Paliers de prix chargés pour l'item: " + itemId + " (format liste)");
                } else {
                    // Essayer de lire comme une section de configuration
                    ConfigurationSection tiersSection = itemSection.getConfigurationSection("tiers");
                    if (tiersSection != null) {
                        for (String key : tiersSection.getKeys(false)) {
                            ConfigurationSection tierSection = tiersSection.getConfigurationSection(key);
                            if (tierSection == null) continue;

                            int soldThreshold = tierSection.getInt("sold_threshold");
                            double multiplier = tierSection.getDouble("price_multiplier");
                            data.addTier(new PriceTier(soldThreshold, multiplier));
                        }

                        data.sortTiers();
                        pricingData.put(itemId, data);
                        plugin.getLogger().info("Paliers de prix chargés pour l'item: " + itemId + " (format section)");
                    } else {
                        plugin.getLogger().warning("Section ou liste de paliers non trouvée pour l'item: " + itemId);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement de la configuration de prix pour: " + itemId, e);
            }
        }

        plugin.getLogger().info("Configuration de prix dynamiques chargée: " + pricingData.size() + " items configurés.");
    }

    private void loadSoldItems() {
        String sql = "SELECT item_id, total_sold FROM global_item_sales";

        try {
            databaseManager.executeQuery(sql, rs -> {
                itemSoldCache.clear();
                while (rs.next()) {
                    itemSoldCache.put(rs.getString("item_id"), rs.getInt("total_sold"));
                }
                return null;
            });

            plugin.getLogger().info("Données de ventes globales chargées: " + itemSoldCache.size() + " items.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des données de ventes globales", e);
        }
    }

    public void updateItemSold(String itemId, int quantity) {
        if (quantity <= 0) return;

        String sql = "INSERT INTO global_item_sales (item_id, total_sold) VALUES (?, ?) " +
                "ON CONFLICT(item_id) DO UPDATE SET total_sold = total_sold + ?";

        final String finalItemId = itemId;
        final int finalQuantity = quantity;

        try {
            databaseManager.executeTransaction(conn -> {
                try {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, finalItemId);
                        ps.setInt(2, finalQuantity);
                        ps.setInt(3, finalQuantity);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException("Erreur lors de la mise à jour des ventes pour l'item: " + finalItemId, e);
                    }

                    try (PreparedStatement ps = conn.prepareStatement("SELECT total_sold FROM global_item_sales WHERE item_id = ?")) {
                        ps.setString(1, finalItemId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                itemSoldCache.put(finalItemId, rs.getInt("total_sold"));
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException("Erreur lors de la récupération du nouveau total vendu pour l'item: " + finalItemId, e);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException("Erreur lors de la préparation de la requête pour l'item: " + finalItemId, e);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Erreur inattendue lors de la mise à jour des ventes pour l'item: " + finalItemId, e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour des ventes pour l'item: " + finalItemId, e);
        }
    }

    public double getPriceMultiplier(String itemId) {
        if (!pricingData.containsKey(itemId)) {
            return 1.0;
        }

        PricingData data = pricingData.get(itemId);
        int totalSold = itemSoldCache.getOrDefault(itemId, 0);

        PriceTier activeTier = null;

        for (PriceTier tier : data.getTiers()) {
            if (totalSold >= tier.getSoldThreshold()) {
                activeTier = tier;
            } else {
                break;
            }
        }

        if (activeTier == null) {
            return 1.0;
        }

        double multiplier = activeTier.getPriceMultiplier();

        return Math.max(multiplier, data.getMinimumMultiplier());
    }

    public int getTotalSold(String itemId) {
        return itemSoldCache.getOrDefault(itemId, 0);
    }

    public boolean hasDynamicPricing(String itemId) {
        return pricingData.containsKey(itemId);
    }

    public static class PricingData {
        private final double minimumMultiplier;
        private final Map<Integer, PriceTier> tiers = new HashMap<>();

        public PricingData(double minimumMultiplier) {
            this.minimumMultiplier = minimumMultiplier;
        }

        public void addTier(PriceTier tier) {
            tiers.put(tier.getSoldThreshold(), tier);
        }

        public void sortTiers() {
            // Pas besoin de trier car les paliers sont accessibles par seuil
        }

        public PriceTier[] getTiers() {
            return tiers.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getSoldThreshold(), a.getSoldThreshold()))
                    .toArray(PriceTier[]::new);
        }

        public double getMinimumMultiplier() {
            return minimumMultiplier;
        }
    }

    public static class PriceTier {
        private final int soldThreshold;
        private final double priceMultiplier;

        public PriceTier(int soldThreshold, double priceMultiplier) {
            this.soldThreshold = soldThreshold;
            this.priceMultiplier = priceMultiplier;
        }

        public int getSoldThreshold() {
            return soldThreshold;
        }

        public double getPriceMultiplier() {
            return priceMultiplier;
        }
    }
}
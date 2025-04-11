package fr.minefest.mineconomy.shop;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
import fr.minefest.mineconomy.config.ConfigManager;
import fr.minefest.mineconomy.database.DatabaseManager;
import fr.minefest.mineconomy.price.DynamicPriceManager;
import fr.minefest.mineconomy.shop.gui.DailyShopGUI;
import fr.minefest.mineconomy.shop.gui.ShopGUI;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ShopManager {
    private final Mineconomy plugin;
    private final DatabaseManager databaseManager;
    private final BankManager bankManager;
    private final DynamicPriceManager priceManager;
    private final ConfigManager configManager;

    private final Map<String, ItemShopData> shopItems = new ConcurrentHashMap<>();
    private final Map<String, ItemDailyShopData> dailyShopItems = new ConcurrentHashMap<>();
    private BukkitTask dailyResetTask;
    private LocalDate currentDay;

    private ShopGUI shopGUI;
    private DailyShopGUI dailyShopGUI;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ShopManager(Mineconomy plugin, DatabaseManager databaseManager, BankManager bankManager,
                       DynamicPriceManager priceManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.bankManager = bankManager;
        this.priceManager = priceManager;
        this.configManager = configManager;
        this.currentDay = LocalDate.now();
    }

    public void initialize() {
        loadShopItems();
        loadDailyShopItems();

        shopGUI = new ShopGUI(plugin, this);
        dailyShopGUI = new DailyShopGUI(plugin, this);

        scheduleDailyReset();

        // Enregistrer les listeners
        plugin.getServer().getPluginManager().registerEvents(shopGUI, plugin);
        plugin.getServer().getPluginManager().registerEvents(dailyShopGUI, plugin);

        plugin.getLogger().info("ShopManager initialisé avec " + shopItems.size() + " items dans le shop principal et "
                + dailyShopItems.size() + " items dans le shop journalier.");
    }

    public void reload() {
        shopItems.clear();
        dailyShopItems.clear();

        if (dailyResetTask != null) {
            dailyResetTask.cancel();
            dailyResetTask = null;
        }

        loadShopItems();
        loadDailyShopItems();
        scheduleDailyReset();

        shopGUI.reload();
        dailyShopGUI.reload();

        plugin.getLogger().info("ShopManager rechargé.");
    }

    private void loadShopItems() {
        ConfigurationSection shopSection = configManager.getShopConfig();
        if (shopSection == null) {
            plugin.getLogger().warning("Section du shop principal non trouvée dans la configuration.");
            return;
        }

        for (String itemKey : shopSection.getKeys(false)) {
            try {
                ConfigurationSection itemSection = shopSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;

                Material material = Material.getMaterial(itemKey);
                if (material == null) {
                    plugin.getLogger().warning("Matériel invalide dans la configuration du shop: " + itemKey);
                    continue;
                }

                double basePrice = itemSection.getDouble("base_price", 0);
                if (basePrice <= 0) {
                    plugin.getLogger().warning("Prix invalide pour l'item " + itemKey + ": " + basePrice);
                    continue;
                }

                boolean useDynamicPricing = itemSection.getBoolean("use_dynamic_pricing", false);
                Map<String, String> requiredNBT = new HashMap<>();

                if (itemSection.contains("required_nbt")) {
                    ConfigurationSection nbtSection = itemSection.getConfigurationSection("required_nbt");
                    if (nbtSection != null) {
                        for (String nbtKey : nbtSection.getKeys(false)) {
                            requiredNBT.put(nbtKey, nbtSection.getString(nbtKey));
                        }
                    }
                }

                int guiSlot = itemSection.getInt("gui_slot", -1);
                ItemStack displayItem = null;

                if (itemSection.contains("gui_item")) {
                    ConfigurationSection guiItemSection = itemSection.getConfigurationSection("gui_item");
                    if (guiItemSection != null) {
                        Material guiMaterial = Material.getMaterial(guiItemSection.getString("material", itemKey));
                        if (guiMaterial != null) {
                            displayItem = new ItemStack(guiMaterial);

                            ItemMeta meta = displayItem.getItemMeta();
                            if (meta != null) {
                                if (guiItemSection.contains("name")) {
                                    meta.setDisplayName(MessageUtil.translate(guiItemSection.getString("name")));
                                }

                                if (guiItemSection.contains("lore")) {
                                    List<String> lore = guiItemSection.getStringList("lore").stream()
                                            .map(MessageUtil::translate)
                                            .collect(Collectors.toList());
                                    meta.setLore(lore);
                                }

                                displayItem.setItemMeta(meta);
                            }
                        }
                    }
                }

                if (displayItem == null) {
                    displayItem = new ItemStack(material);
                }

                ItemShopData itemData = new ItemShopData(
                        itemKey,
                        material,
                        basePrice,
                        useDynamicPricing,
                        requiredNBT,
                        guiSlot,
                        displayItem
                );

                shopItems.put(itemKey, itemData);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement de l'item du shop: " + itemKey, e);
            }
        }
    }

    private void loadDailyShopItems() {
        dailyShopItems.clear();

        int currentDayNumber = configManager.getCurrentDayNumber();
        ConfigurationSection dayShopSection = configManager.getCurrentDayShopConfig();

        if (dayShopSection == null) {
            plugin.getLogger().warning("Configuration du shop du jour non trouvée. Le shop journalier sera désactivé.");
            return;
        }

        ConfigurationSection itemsSection = dayShopSection.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("Section 'items' non trouvée dans la configuration du shop du jour.");
            return;
        }

        for (String itemKey : itemsSection.getKeys(false)) {
            try {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;

                Material material = Material.getMaterial(itemKey);
                if (material == null) {
                    plugin.getLogger().warning("Matériel invalide dans la configuration du shop du jour: " + itemKey);
                    continue;
                }

                double price = itemSection.getDouble("price", 0);
                if (price <= 0) {
                    plugin.getLogger().warning("Prix invalide pour l'item du jour " + itemKey + ": " + price);
                    continue;
                }

                int teamSellLimit = itemSection.getInt("team_sell_limit", 0);
                if (teamSellLimit <= 0) {
                    plugin.getLogger().warning("Limite de vente par équipe invalide pour l'item du jour " + itemKey + ": " + teamSellLimit);
                    continue;
                }

                Map<String, String> requiredNBT = new HashMap<>();

                if (itemSection.contains("required_nbt")) {
                    ConfigurationSection nbtSection = itemSection.getConfigurationSection("required_nbt");
                    if (nbtSection != null) {
                        for (String nbtKey : nbtSection.getKeys(false)) {
                            requiredNBT.put(nbtKey, nbtSection.getString(nbtKey));
                        }
                    }
                }

                int guiSlot = itemSection.getInt("gui_slot", -1);
                ItemStack displayItem = null;

                if (itemSection.contains("gui_item")) {
                    ConfigurationSection guiItemSection = itemSection.getConfigurationSection("gui_item");
                    if (guiItemSection != null) {
                        Material guiMaterial = Material.getMaterial(guiItemSection.getString("material", itemKey));
                        if (guiMaterial != null) {
                            displayItem = new ItemStack(guiMaterial);

                            ItemMeta meta = displayItem.getItemMeta();
                            if (meta != null) {
                                if (guiItemSection.contains("name")) {
                                    meta.setDisplayName(MessageUtil.translate(guiItemSection.getString("name")));
                                }

                                if (guiItemSection.contains("lore")) {
                                    List<String> lore = guiItemSection.getStringList("lore").stream()
                                            .map(MessageUtil::translate)
                                            .collect(Collectors.toList());
                                    meta.setLore(lore);
                                }

                                displayItem.setItemMeta(meta);
                            }
                        }
                    }
                }

                if (displayItem == null) {
                    displayItem = new ItemStack(material);
                }

                ItemDailyShopData itemData = new ItemDailyShopData(
                        itemKey,
                        material,
                        price,
                        teamSellLimit,
                        requiredNBT,
                        guiSlot,
                        displayItem
                );

                dailyShopItems.put(itemKey, itemData);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement de l'item du shop du jour: " + itemKey, e);
            }
        }
    }

    private void scheduleDailyReset() {
        if (dailyResetTask != null) {
            dailyResetTask.cancel();
        }

        // Programmer la réinitialisation pour minuit
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long secondsUntilMidnight = java.time.Duration.between(now, nextMidnight).getSeconds();

        dailyResetTask = Bukkit.getScheduler().runTaskLater(plugin, this::performDailyReset, secondsUntilMidnight * 20);

        plugin.getLogger().info("Prochaine réinitialisation du shop journalier dans " + secondsUntilMidnight + " secondes.");
    }

    public void performDailyReset() {
        try {
            currentDay = LocalDate.now();

            // Charger le shop du jour suivant
            configManager.loadCurrentDayShop();
            loadDailyShopItems();

            // Réinitialiser les limites de vente journalières
            resetDailyTeamLimits();

            // Replanifier pour le jour suivant
            scheduleDailyReset();

            // Recharger le GUI
            if (dailyShopGUI != null) {
                dailyShopGUI.reload();
            }

            plugin.getLogger().info("Shop journalier réinitialisé avec succès pour le jour "
                    + configManager.getCurrentDayNumber() + " (" + currentDay + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la réinitialisation du shop journalier", e);
        }
    }

    private void resetDailyTeamLimits() {
        String sql = "DELETE FROM daily_team_limits WHERE sale_date = ?";

        try {
            databaseManager.executeUpdate(sql, currentDay.format(DATE_FORMATTER));
            plugin.getLogger().info("Limites de vente journalières réinitialisées pour " + currentDay);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la réinitialisation des limites de vente journalières", e);
        }
    }

    public boolean sellItemFromDefaultShop(Player player, String itemId, int quantity) {
        if (quantity <= 0) return false;

        ItemShopData itemData = shopItems.get(itemId);
        if (itemData == null) {
            return false;
        }

        String teamName = bankManager.getPlayerTeam(player);
        if (teamName == null) {
            MessageUtil.sendMessage(player, "error.no_team");
            return false;
        }

        Material material = itemData.getMaterial();
        if (material == null) {
            MessageUtil.sendMessage(player, "error.invalid_item");
            return false;
        }

        // Vérifier si le joueur possède assez d'items
        int playerItemCount = countItemsInInventory(player, material, itemData.getRequiredNBT());
        if (playerItemCount < quantity) {
            MessageUtil.sendMessage(player, "error.not_enough_items");
            return false;
        }

        // Calculer le prix final avec palier si nécessaire
        double basePrice = itemData.getBasePrice();
        double finalPrice = basePrice;

        if (itemData.isUseDynamicPricing() && priceManager.hasDynamicPricing(itemId)) {
            double multiplier = priceManager.getPriceMultiplier(itemId);
            finalPrice = basePrice * multiplier;
        }

        final double totalPrice = finalPrice * quantity;
        final String finalTeamName = teamName;
        final Material finalMaterial = material;

        try {
            // Exécuter la transaction complète (retrait des items, crédit de la banque, mise à jour des ventes globales, mise à jour des contributions)
            databaseManager.executeTransaction(connection -> {
                try {
                    // 1. Retirer les items de l'inventaire du joueur
                    removeItemsFromInventory(player, finalMaterial, itemData.getRequiredNBT(), quantity);

                    // 2. Créditer la banque de l'équipe
                    String bankName = bankManager.getDefaultBankName();
                    creditTeamBank(connection, finalTeamName, bankName, totalPrice);

                    // 3. Mettre à jour les ventes globales pour les paliers
                    if (itemData.isUseDynamicPricing()) {
                        updateGlobalItemSales(connection, itemId, quantity);
                    }

                    // 4. Mettre à jour les contributions du joueur
                    updatePlayerContribution(connection, player.getUniqueId(), finalTeamName, bankName, totalPrice);
                } catch (SQLException e) {
                    throw new RuntimeException("Erreur SQL lors de la vente d'items: " + e.getMessage(), e);
                }
            });

            // Message de succès
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%quantity%", String.valueOf(quantity));
            placeholders.put("%item%", formatItemName(itemData.getMaterial()));
            placeholders.put("%price%", MessageUtil.formatMoney(totalPrice, bankManager.getBankCurrency(bankManager.getDefaultBankName())));
            placeholders.put("%team%", finalTeamName);

            MessageUtil.sendMessage(player, "shop.sell_success", placeholders);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la vente d'items: "
                    + player.getName() + ", " + itemId + ", " + quantity, e);
            MessageUtil.sendMessage(player, "error.transaction_failed");
            return false;
        }
    }

    public boolean sellItemFromDailyShop(Player player, String itemId, int quantity) {
        if (quantity <= 0) return false;

        ItemDailyShopData itemData = dailyShopItems.get(itemId);
        if (itemData == null) {
            return false;
        }

        String teamName = bankManager.getPlayerTeam(player);
        if (teamName == null) {
            MessageUtil.sendMessage(player, "error.no_team");
            return false;
        }

        Material material = itemData.getMaterial();
        if (material == null) {
            MessageUtil.sendMessage(player, "error.invalid_item");
            return false;
        }

        // Vérifier si le joueur possède assez d'items
        int playerItemCount = countItemsInInventory(player, material, itemData.getRequiredNBT());
        if (playerItemCount < quantity) {
            MessageUtil.sendMessage(player, "error.not_enough_items");
            return false;
        }

        // Vérifier la limite de vente par équipe
        int teamSellLimit = itemData.getTeamSellLimit();
        int alreadySold = getTeamSoldItemCount(teamName, itemId);
        int canSell = Math.min(quantity, teamSellLimit - alreadySold);

        if (canSell <= 0) {
            MessageUtil.sendMessage(player, "error.team_limit_reached");
            return false;
        }

        // Ajuster la quantité si nécessaire
        final int finalQuantity = canSell < quantity ? canSell : quantity;
        final double totalPrice = itemData.getPrice() * finalQuantity;
        final String finalTeamName = teamName;
        final Material finalMaterial = material;
        final int finalAlreadySold = alreadySold;

        try {
            // Exécuter la transaction complète
            databaseManager.executeTransaction(connection -> {
                try {
                    // 1. Retirer les items de l'inventaire du joueur
                    removeItemsFromInventory(player, finalMaterial, itemData.getRequiredNBT(), finalQuantity);

                    // 2. Créditer la banque de l'équipe
                    String bankName = bankManager.getDefaultBankName();
                    creditTeamBank(connection, finalTeamName, bankName, totalPrice);

                    // 3. Mettre à jour les limites de vente journalières
                    updateDailyTeamLimits(connection, finalTeamName, itemId, finalQuantity);

                    // 4. Mettre à jour les contributions du joueur
                    updatePlayerContribution(connection, player.getUniqueId(), finalTeamName, bankName, totalPrice);
                } catch (SQLException e) {
                    throw new RuntimeException("Erreur SQL lors de la vente d'items: " + e.getMessage(), e);
                }
            });

            // Message de succès
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%quantity%", String.valueOf(finalQuantity));
            placeholders.put("%item%", formatItemName(itemData.getMaterial()));
            placeholders.put("%price%", MessageUtil.formatMoney(totalPrice, bankManager.getBankCurrency(bankManager.getDefaultBankName())));
            placeholders.put("%team%", finalTeamName);
            placeholders.put("%remaining%", String.valueOf(teamSellLimit - finalAlreadySold - finalQuantity));

            MessageUtil.sendMessage(player, "daily.sell_success", placeholders);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la vente d'items au shop journalier: "
                    + player.getName() + ", " + itemId + ", " + finalQuantity, e);
            MessageUtil.sendMessage(player, "error.transaction_failed");
            return false;
        }
    }

    private int countItemsInInventory(Player player, Material material, Map<String, String> requiredNBT) {
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (matchesNBT(item, requiredNBT)) {
                    count += item.getAmount();
                }
            }
        }

        return count;
    }

    private boolean matchesNBT(ItemStack item, Map<String, String> requiredNBT) {
        if (requiredNBT.isEmpty()) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        for (Map.Entry<String, String> entry : requiredNBT.entrySet()) {
            NamespacedKey key = new NamespacedKey(plugin, entry.getKey());
            String value = container.get(key, PersistentDataType.STRING);

            if (value == null || !value.equals(entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    private void removeItemsFromInventory(Player player, Material material, Map<String, String> requiredNBT, int quantity) {
        int remaining = quantity;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];

            if (item != null && item.getType() == material && matchesNBT(item, requiredNBT)) {
                int amount = item.getAmount();

                if (amount <= remaining) {
                    remaining -= amount;
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(amount - remaining);
                    remaining = 0;
                }
            }
        }

        player.updateInventory();
    }

    private void creditTeamBank(Connection connection, String teamName, String bankName, double amount) throws SQLException {
        String sql = "INSERT INTO team_banks (team_name, bank_name, balance) VALUES (?, ?, ?) "
                + "ON CONFLICT(team_name, bank_name) DO UPDATE SET balance = balance + ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, teamName);
            ps.setString(2, bankName);
            ps.setDouble(3, amount);
            ps.setDouble(4, amount);
            ps.executeUpdate();
        }
    }

    private void updateGlobalItemSales(Connection connection, String itemId, int quantity) throws SQLException {
        String sql = "INSERT INTO global_item_sales (item_id, total_sold) VALUES (?, ?) "
                + "ON CONFLICT(item_id) DO UPDATE SET total_sold = total_sold + ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setInt(2, quantity);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    private void updatePlayerContribution(Connection connection, UUID playerUuid, String teamName, String bankName, double amount) throws SQLException {
        String sql = "INSERT INTO player_contributions (player_uuid, team_name, bank_name, amount_contributed) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, team_name, bank_name) "
                + "DO UPDATE SET amount_contributed = amount_contributed + ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, teamName);
            ps.setString(3, bankName);
            ps.setDouble(4, amount);
            ps.setDouble(5, amount);
            ps.executeUpdate();
        }
    }

    private void updateDailyTeamLimits(Connection connection, String teamName, String itemId, int quantity) throws SQLException {
        String sql = "INSERT INTO daily_team_limits (sale_date, team_name, item_id, quantity_sold) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(sale_date, team_name, item_id) "
                + "DO UPDATE SET quantity_sold = quantity_sold + ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, currentDay.format(DATE_FORMATTER));
            ps.setString(2, teamName);
            ps.setString(3, itemId);
            ps.setInt(4, quantity);
            ps.setInt(5, quantity);
            ps.executeUpdate();
        }
    }

    public int getTeamSoldItemCount(String teamName, String itemId) {
        String sql = "SELECT quantity_sold FROM daily_team_limits "
                + "WHERE sale_date = ? AND team_name = ? AND item_id = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                if (rs.next()) {
                    return rs.getInt("quantity_sold");
                }
                return 0;
            }, currentDay.format(DATE_FORMATTER), teamName, itemId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des quantités vendues: "
                    + teamName + ", " + itemId, e);
            return 0;
        }
    }

    public Map<String, Integer> getTeamDailyLimits(String teamName) {
        String sql = "SELECT item_id, quantity_sold FROM daily_team_limits "
                + "WHERE sale_date = ? AND team_name = ?";

        try {
            return databaseManager.executeQuery(sql, rs -> {
                Map<String, Integer> limits = new HashMap<>();
                while (rs.next()) {
                    limits.put(rs.getString("item_id"), rs.getInt("quantity_sold"));
                }
                return limits;
            }, currentDay.format(DATE_FORMATTER), teamName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des limites journalières: " + teamName, e);
            return Collections.emptyMap();
        }
    }

    public boolean isShopItemValid(String itemId) {
        return shopItems.containsKey(itemId);
    }

    public boolean isDailyShopItemValid(String itemId) {
        return dailyShopItems.containsKey(itemId);
    }

    public double getItemBasePrice(String itemId) {
        ItemShopData data = shopItems.get(itemId);
        return data != null ? data.getBasePrice() : 0;
    }

    public double getDailyItemPrice(String itemId) {
        ItemDailyShopData data = dailyShopItems.get(itemId);
        return data != null ? data.getPrice() : 0;
    }

    public int getDailyItemLimit(String itemId) {
        ItemDailyShopData data = dailyShopItems.get(itemId);
        return data != null ? data.getTeamSellLimit() : 0;
    }

    public Collection<ItemShopData> getShopItems() {
        return shopItems.values();
    }

    public Collection<ItemDailyShopData> getDailyShopItems() {
        return dailyShopItems.values();
    }

    public boolean openShop(Player player) {
        if (!player.hasPermission("minefestshop.user.shop")) {
            MessageUtil.sendMessage(player, "error.no_permission");
            return false;
        }

        shopGUI.openShop(player);
        return true;
    }

    public boolean openDailyShop(Player player) {
        if (!player.hasPermission("minefestshop.user.daily")) {
            MessageUtil.sendMessage(player, "error.no_permission");
            return false;
        }

        if (dailyShopItems.isEmpty()) {
            MessageUtil.sendMessage(player, "error.daily_shop_unavailable");
            return false;
        }

        dailyShopGUI.openShop(player);
        return true;
    }

    public void setDay(int dayNumber) {
        configManager.setCurrentDayNumber(dayNumber);
        loadDailyShopItems();
        resetDailyTeamLimits();

        if (dailyShopGUI != null) {
            dailyShopGUI.reload();
        }
    }

    private String formatItemName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    // Méthode pour vendre tout l'inventaire d'un joueur qui peut être vendu au shop principal
    public boolean sellAllItems(Player player) {
        if (player == null) return false;

        String teamName = bankManager.getPlayerTeam(player);
        if (teamName == null) {
            MessageUtil.sendMessage(player, "error.no_team");
            return false;
        }

        Map<String, Integer> itemsToSell = new HashMap<>();

        // Identifier tous les items vendables dans l'inventaire du joueur
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            String materialName = item.getType().name();

            if (shopItems.containsKey(materialName)) {
                ItemShopData data = shopItems.get(materialName);

                if (matchesNBT(item, data.getRequiredNBT())) {
                    itemsToSell.put(materialName, itemsToSell.getOrDefault(materialName, 0) + item.getAmount());
                }
            }
        }

        if (itemsToSell.isEmpty()) {
            MessageUtil.sendMessage(player, "shop.nothing_to_sell");
            return false;
        }

        final String finalTeamName = teamName;
        final Map<String, Integer> finalItemsToSell = new HashMap<>(itemsToSell);
        final double[] totalValues = {0.0, 0.0}; // [earnings, itemCount]

        try {
            databaseManager.executeTransaction(connection -> {
                try {
                    double earnings = 0;
                    int itemsSold = 0;
                    String bankName = bankManager.getDefaultBankName();

                    for (Map.Entry<String, Integer> entry : finalItemsToSell.entrySet()) {
                        String itemId = entry.getKey();
                        int quantity = entry.getValue();

                        ItemShopData data = shopItems.get(itemId);
                        double basePrice = data.getBasePrice();
                        double finalPrice = basePrice;

                        if (data.isUseDynamicPricing() && priceManager.hasDynamicPricing(itemId)) {
                            double multiplier = priceManager.getPriceMultiplier(itemId);
                            finalPrice = basePrice * multiplier;
                        }

                        double itemTotal = finalPrice * quantity;

                        // Retirer les items de l'inventaire
                        removeItemsFromInventory(player, data.getMaterial(), data.getRequiredNBT(), quantity);

                        // Mettre à jour les ventes globales pour les paliers si nécessaire
                        if (data.isUseDynamicPricing()) {
                            updateGlobalItemSales(connection, itemId, quantity);
                        }

                        earnings += itemTotal;
                        itemsSold += quantity;
                    }

                    // Créditer la banque de l'équipe avec le montant total
                    if (earnings > 0) {
                        creditTeamBank(connection, finalTeamName, bankName, earnings);
                        updatePlayerContribution(connection, player.getUniqueId(), finalTeamName, bankName, earnings);
                    }

                    // Store the values for use outside the lambda
                    totalValues[0] = earnings;
                    totalValues[1] = itemsSold;
                } catch (SQLException e) {
                    throw new RuntimeException("Erreur SQL lors de la vente de tous les items: " + e.getMessage(), e);
                }
            });

            // Message de succès
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%quantity%", String.valueOf((int)totalValues[1]));
            placeholders.put("%price%", MessageUtil.formatMoney(totalValues[0], bankManager.getBankCurrency(bankManager.getDefaultBankName())));
            placeholders.put("%team%", finalTeamName);

            MessageUtil.sendMessage(player, "shop.sell_all_success", placeholders);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la vente de tous les items: " + player.getName(), e);
            MessageUtil.sendMessage(player, "error.transaction_failed");
            return false;
        }
    }

    // Méthode pour vendre tout d'un item spécifique
    public boolean sellAllOfType(Player player, String itemId) {
        if (player == null || !shopItems.containsKey(itemId)) return false;

        ItemShopData data = shopItems.get(itemId);
        int quantity = countItemsInInventory(player, data.getMaterial(), data.getRequiredNBT());

        if (quantity <= 0) {
            MessageUtil.sendMessage(player, "error.not_enough_items");
            return false;
        }

        return sellItemFromDefaultShop(player, itemId, quantity);
    }

    // Classe statique pour les données des items du shop principal
    public static class ItemShopData {
        private final String itemId;
        private final Material material;
        private final double basePrice;
        private final boolean useDynamicPricing;
        private final Map<String, String> requiredNBT;
        private final int guiSlot;
        private final ItemStack displayItem;

        public ItemShopData(String itemId, Material material, double basePrice, boolean useDynamicPricing,
                            Map<String, String> requiredNBT, int guiSlot, ItemStack displayItem) {
            this.itemId = itemId;
            this.material = material;
            this.basePrice = basePrice;
            this.useDynamicPricing = useDynamicPricing;
            this.requiredNBT = requiredNBT;
            this.guiSlot = guiSlot;
            this.displayItem = displayItem;
        }

        public String getItemId() {
            return itemId;
        }

        public Material getMaterial() {
            return material;
        }

        public double getBasePrice() {
            return basePrice;
        }

        public boolean isUseDynamicPricing() {
            return useDynamicPricing;
        }

        public Map<String, String> getRequiredNBT() {
            return requiredNBT;
        }

        public int getGuiSlot() {
            return guiSlot;
        }

        public ItemStack getDisplayItem() {
            return displayItem.clone();
        }
    }

    // Classe statique pour les données des items du shop journalier
    public static class ItemDailyShopData {
        private final String itemId;
        private final Material material;
        private final double price;
        private final int teamSellLimit;
        private final Map<String, String> requiredNBT;
        private final int guiSlot;
        private final ItemStack displayItem;

        public ItemDailyShopData(String itemId, Material material, double price, int teamSellLimit,
                                 Map<String, String> requiredNBT, int guiSlot, ItemStack displayItem) {
            this.itemId = itemId;
            this.material = material;
            this.price = price;
            this.teamSellLimit = teamSellLimit;
            this.requiredNBT = requiredNBT;
            this.guiSlot = guiSlot;
            this.displayItem = displayItem;
        }

        public String getItemId() {
            return itemId;
        }

        public Material getMaterial() {
            return material;
        }

        public double getPrice() {
            return price;
        }

        public int getTeamSellLimit() {
            return teamSellLimit;
        }

        public Map<String, String> getRequiredNBT() {
            return requiredNBT;
        }

        public int getGuiSlot() {
            return guiSlot;
        }

        public ItemStack getDisplayItem() {
            return displayItem.clone();
        }
    }
}
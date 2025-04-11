package fr.minefest.mineconomy.shop.gui;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.shop.ShopManager;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopGUI implements Listener {
    private final Mineconomy plugin;
    private final ShopManager shopManager;
    private final Map<UUID, ShopInventoryHolder> openInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingUpdates = new ConcurrentHashMap<>();

    private final int rows;
    private final ItemStack fillItem;
    private final ItemStack sellAllItem;
    private static final long CLICK_COOLDOWN = 250;

    public ShopGUI(Mineconomy plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;

        this.rows = plugin.getConfigManager().getConfig(fr.minefest.mineconomy.config.ConfigManager.ConfigType.CONFIG)
                .getInt("gui.shop_rows", 6);

        String fillMaterialName = plugin.getConfigManager().getConfig(fr.minefest.mineconomy.config.ConfigManager.ConfigType.CONFIG)
                .getString("gui.fill_item", "BLACK_STAINED_GLASS_PANE");
        Material fillMaterial = Material.getMaterial(fillMaterialName);
        if (fillMaterial == null) fillMaterial = Material.BLACK_STAINED_GLASS_PANE;

        this.fillItem = new ItemStack(fillMaterial);
        ItemMeta fillMeta = fillItem.getItemMeta();
        if (fillMeta != null) {
            fillMeta.setDisplayName(" ");
            fillItem.setItemMeta(fillMeta);
        }

        String sellAllMaterialName = plugin.getConfigManager().getConfig(fr.minefest.mineconomy.config.ConfigManager.ConfigType.CONFIG)
                .getString("gui.sell_all_item", "GOLD_INGOT");
        Material sellAllMaterial = Material.getMaterial(sellAllMaterialName);
        if (sellAllMaterial == null) sellAllMaterial = Material.GOLD_INGOT;

        this.sellAllItem = new ItemStack(sellAllMaterial);
        ItemMeta sellAllMeta = sellAllItem.getItemMeta();
        if (sellAllMeta != null) {
            sellAllMeta.setDisplayName(MessageUtil.translate("&e&lVendre tout"));
            sellAllMeta.setLore(Arrays.asList(
                    MessageUtil.translate("&7Cliquez pour vendre tous les"),
                    MessageUtil.translate("&7items vendables de votre inventaire")
            ));
            sellAllItem.setItemMeta(sellAllMeta);
        }
    }

    public void reload() {
        for (UUID uuid : new HashSet<>(openInventories.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }

            BukkitTask task = pendingUpdates.remove(uuid);
            if (task != null) {
                task.cancel();
            }
        }

        openInventories.clear();
        lastClickTime.clear();
        pendingUpdates.clear();
    }

    public void openShop(Player player) {
        Inventory inventory = createShopInventory(player);
        player.openInventory(inventory);
    }

    private Inventory createShopInventory(Player player) {
        String title = MessageUtil.getGuiTitle("shop");
        ShopInventoryHolder holder = new ShopInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem.clone());
        }

        inventory.setItem(inventory.getSize() - 5, sellAllItem.clone());

        Collection<ShopManager.ItemShopData> items = shopManager.getShopItems();

        for (ShopManager.ItemShopData item : items) {
            int slot = item.getGuiSlot();

            if (slot < 0 || slot >= inventory.getSize() ||
                    (inventory.getItem(slot) != null && !isItemSimilar(inventory.getItem(slot), fillItem))) {
                slot = findNextAvailableSlot(inventory);
                if (slot == -1) break;
            }

            ItemStack displayItem = createDisplayItem(item);
            inventory.setItem(slot, displayItem);
            holder.addItemSlot(slot, item.getItemId());
        }

        holder.setInventory(inventory);
        openInventories.put(player.getUniqueId(), holder);
        return inventory;
    }

    private boolean isItemSimilar(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;

        ItemMeta metaA = a.getItemMeta();
        ItemMeta metaB = b.getItemMeta();

        if (metaA == null && metaB == null) return true;
        if (metaA == null || metaB == null) return false;

        if (!Objects.equals(metaA.getDisplayName(), metaB.getDisplayName())) return false;

        return true;
    }

    private ItemStack createDisplayItem(ShopManager.ItemShopData item) {
        ItemStack displayItem = item.getDisplayItem().clone();
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() :
                    MessageUtil.translate("&b" + formatItemName(item.getMaterial()));

            meta.setDisplayName(itemName);

            double basePrice = item.getBasePrice();
            double finalPrice = basePrice;

            if (item.isUseDynamicPricing() && plugin.getPriceManager().hasDynamicPricing(item.getItemId())) {
                double multiplier = plugin.getPriceManager().getPriceMultiplier(item.getItemId());
                finalPrice = basePrice * multiplier;
            }

            String currencySymbol = plugin.getBankManager().getBankCurrency(plugin.getBankManager().getDefaultBankName());

            List<String> lore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                lore.addAll(meta.getLore());
            }

            lore.add(MessageUtil.translate("&7Prix: &e" + MessageUtil.formatMoney(finalPrice, currencySymbol)));

            if (item.isUseDynamicPricing()) {
                double multiplier = plugin.getPriceManager().getPriceMultiplier(item.getItemId());
                if (multiplier < 1.0) {
                    int percentReduction = (int) ((1.0 - multiplier) * 100);
                    lore.add(MessageUtil.translate("&7Réduction: &c-" + percentReduction + "%"));
                }

                int totalSold = plugin.getPriceManager().getTotalSold(item.getItemId());
                lore.add(MessageUtil.translate("&7Vendus: &e" + totalSold));
            }

            lore.add("");
            lore.add(MessageUtil.translate("&7Clic gauche: &fVendre 1"));
            lore.add(MessageUtil.translate("&7Clic droit: &fVendre 8"));
            lore.add(MessageUtil.translate("&7Shift+Clic gauche: &fVendre 16"));
            lore.add(MessageUtil.translate("&7Shift+Clic droit: &fVendre 64"));
            lore.add(MessageUtil.translate("&7Clic molette: &fVendre tout"));

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private int findNextAvailableSlot(Inventory inventory) {
        for (int i = 10; i < inventory.getSize() - 9; i++) {
            if (i % 9 == 0 || i % 9 == 8) continue;

            ItemStack item = inventory.getItem(i);
            if (item == null || isItemSimilar(item, fillItem)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!openInventories.containsKey(playerUUID)) return;

        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder)) return;

        // Annuler toute action qui pourrait manipuler l'inventaire du haut
        if (event.getView().getTopInventory().equals(event.getClickedInventory())) {
            event.setCancelled(true);

            // Traiter uniquement les clics dans l'inventaire du shop
            int slot = event.getSlot();
            ShopInventoryHolder holder = openInventories.get(playerUUID);

            // Vérifier si le slot est valide
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

            // Vérifier le cooldown
            long currentTime = System.currentTimeMillis();
            if (lastClickTime.containsKey(playerUUID) && currentTime - lastClickTime.get(playerUUID) < CLICK_COOLDOWN) {
                return;
            }
            lastClickTime.put(playerUUID, currentTime);

            // Bouton "Vendre tout"
            if (slot == event.getView().getTopInventory().getSize() - 5 &&
                    event.getCurrentItem() != null &&
                    event.getCurrentItem().getType() == sellAllItem.getType()) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean success = shopManager.sellAllItems(player);
                    if (success) {
                        updateInventory(player);
                    }
                });
                return;
            }

            // Item du shop
            String itemId = holder.getItemIdForSlot(slot);
            if (itemId != null) {
                int quantity = getQuantityFromClick(event.getClick());

                if (quantity > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        boolean success;
                        if (event.getClick() == ClickType.MIDDLE) {
                            success = shopManager.sellAllOfType(player, itemId);
                        } else {
                            success = shopManager.sellItemFromDefaultShop(player, itemId, quantity);
                        }

                        if (success) {
                            updateInventory(player);
                        }
                    });
                }
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            // Bloquer le shift-click depuis l'inventaire du joueur vers l'inventaire du shop
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!openInventories.containsKey(playerUUID)) return;

        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder)) return;

        // Vérifier si l'un des slots affectés est dans l'inventaire du haut
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        openInventories.remove(uuid);
        lastClickTime.remove(uuid);

        BukkitTask task = pendingUpdates.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private int getQuantityFromClick(ClickType clickType) {
        switch (clickType) {
            case LEFT:
                return 1;
            case RIGHT:
                return 8;
            case SHIFT_LEFT:
                return 16;
            case SHIFT_RIGHT:
                return 64;
            case MIDDLE:
                return -1;
            default:
                return 0;
        }
    }

    private void updateInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        // Annuler toute mise à jour en attente
        BukkitTask pendingTask = pendingUpdates.remove(uuid);
        if (pendingTask != null) {
            pendingTask.cancel();
        }

        // Créer une nouvelle tâche de mise à jour
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                // Fermer l'inventaire actuel
                player.closeInventory();

                // Ouvrir un nouvel inventaire après un court délai
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        openShop(player);
                        pendingUpdates.remove(uuid);
                    }
                }, 1L);
            }
        });

        pendingUpdates.put(uuid, task);
    }

    private String formatItemName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    public static class ShopInventoryHolder implements InventoryHolder {
        private final Map<Integer, String> slotToItemId = new HashMap<>();
        private Inventory inventory;

        public void addItemSlot(int slot, String itemId) {
            slotToItemId.put(slot, itemId);
        }

        public String getItemIdForSlot(int slot) {
            return slotToItemId.get(slot);
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
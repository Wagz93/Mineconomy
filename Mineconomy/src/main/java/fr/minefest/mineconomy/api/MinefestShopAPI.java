package fr.minefest.mineconomy.api;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * API publique pour le plugin Mineconomy
 * Permet aux autres plugins d'interagir avec les systèmes de banque et de shop
 */
public class MinefestShopAPI {
    private final Mineconomy plugin;
    private final BankManager bankManager;

    public MinefestShopAPI(Mineconomy plugin) {
        this.plugin = plugin;
        this.bankManager = plugin.getBankManager();
    }

    /**
     * Récupère le solde d'une banque d'équipe
     */
    public double getBankBalance(String teamName, String bankName) {
        return bankManager.getBankBalance(teamName, bankName);
    }

    /**
     * Ajoute un montant à une banque d'équipe
     */
    public boolean addBankBalance(String teamName, String bankName, double amount) {
        return bankManager.addBankBalance(teamName, bankName, amount);
    }

    /**
     * Retire un montant d'une banque d'équipe
     */
    public boolean removeBankBalance(String teamName, String bankName, double amount) {
        return bankManager.removeBankBalance(teamName, bankName, amount);
    }

    /**
     * Définit le solde d'une banque d'équipe
     */
    public boolean setBankBalance(String teamName, String bankName, double amount) {
        return bankManager.setBankBalance(teamName, bankName, amount);
    }

    /**
     * Récupère le symbole de devise d'une banque
     */
    public String getBankCurrency(String bankName) {
        return bankManager.getBankCurrency(bankName);
    }

    /**
     * Récupère tous les comptes bancaires d'une équipe
     */
    public List<String> getTeamBankAccounts(String teamName) {
        return bankManager.getTeamBankAccounts(teamName).keySet().stream().toList();
    }

    /**
     * Récupère tous les types de banques
     */
    public List<String> getAllBankTypes() {
        return bankManager.getAllBankTypes();
    }

    /**
     * Récupère la contribution d'un joueur à une banque
     */
    public double getPlayerContribution(UUID playerUuid, String teamName, String bankName) {
        return bankManager.getPlayerContribution(playerUuid, teamName, bankName);
    }

    /**
     * Ouvre le shop principal pour un joueur
     */
    public boolean openShop(Player player) {
        return plugin.getShopManager().openShop(player);
    }

    /**
     * Ouvre le shop journalier pour un joueur
     */
    public boolean openDailyShop(Player player) {
        return plugin.getShopManager().openDailyShop(player);
    }

    /**
     * Récupère le prix actuel d'un item dans le shop principal
     */
    public double getDefaultShopItemPrice(String itemId) {
        double basePrice = plugin.getShopManager().getItemBasePrice(itemId);
        
        if (basePrice <= 0) return 0;
        
        if (plugin.getPriceManager().hasDynamicPricing(itemId)) {
            double multiplier = plugin.getPriceManager().getPriceMultiplier(itemId);
            return basePrice * multiplier;
        }
        
        return basePrice;
    }

    /**
     * Récupère le prix d'un item dans le shop journalier
     */
    public double getDailyShopItemPrice(String itemId) {
        return plugin.getShopManager().getDailyItemPrice(itemId);
    }

    /**
     * Vérifie si un item utilise les prix dynamiques
     */
    public boolean hasDynamicPricing(String itemId) {
        return plugin.getPriceManager().hasDynamicPricing(itemId);
    }

    /**
     * Récupère le nombre total d'un item vendu (pour les paliers)
     */
    public int getTotalItemsSold(String itemId) {
        return plugin.getPriceManager().getTotalSold(itemId);
    }
}
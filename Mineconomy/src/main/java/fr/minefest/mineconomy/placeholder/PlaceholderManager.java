package fr.minefest.mineconomy.placeholder;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
import fr.minefest.mineconomy.bank.BankManager.TeamBalance;
import fr.minefest.mineconomy.util.MessageUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderManager extends PlaceholderExpansion {
    private final Mineconomy plugin;

    // Cache pour les classements pour éviter de requêter la base de données trop souvent
    private final Map<String, List<TeamBalance>> rankingsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimes = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 60000; // 1 minute

    private static final Pattern TOP_MONEY_PATTERN = Pattern.compile("top_money_(.+)_(\\d+)");
    private static final Pattern TOP_NAME_PATTERN = Pattern.compile("top_name_(.+)_(\\d+)");

    public PlaceholderManager(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "minefestshop";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private List<TeamBalance> getTopTeamsWithCache(String bankName, int limit) {
        String cacheKey = bankName + "_" + limit;
        long currentTime = System.currentTimeMillis();

        // Vérifier si le cache est toujours valide
        if (rankingsCache.containsKey(cacheKey) &&
                (currentTime - cacheTimes.getOrDefault(cacheKey, 0L)) < CACHE_EXPIRY_MS) {
            return rankingsCache.get(cacheKey);
        }

        // Récupérer les données fraîches
        List<TeamBalance> topTeams = plugin.getBankManager().getTopTeams(bankName, limit);

        // Mettre en cache
        rankingsCache.put(cacheKey, topTeams);
        cacheTimes.put(cacheKey, currentTime);

        return topTeams;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) return "";

        BankManager bankManager = plugin.getBankManager();
        String teamName = bankManager.getPlayerTeam(player);

        // Récupérer le top money pour une position donnée
        Matcher moneyMatcher = TOP_MONEY_PATTERN.matcher(identifier);
        if (moneyMatcher.matches()) {
            String bankName = moneyMatcher.group(1);
            int position;

            try {
                position = Integer.parseInt(moneyMatcher.group(2));
                if (position < 1) return "0";
            } catch (NumberFormatException e) {
                return "0";
            }

            List<TeamBalance> topTeams = getTopTeamsWithCache(bankName, Math.max(position, 10));

            if (topTeams.size() < position) {
                return "0";
            }

            TeamBalance team = topTeams.get(position - 1);
            return MessageUtil.formatMoney(team.getBalance(), bankManager.getBankCurrency(bankName));
        }

        // Récupérer le nom de l'équipe pour une position donnée
        Matcher nameMatcher = TOP_NAME_PATTERN.matcher(identifier);
        if (nameMatcher.matches()) {
            String bankName = nameMatcher.group(1);
            int position;

            try {
                position = Integer.parseInt(nameMatcher.group(2));
                if (position < 1) return "Aucune";
            } catch (NumberFormatException e) {
                return "Aucune";
            }

            List<TeamBalance> topTeams = getTopTeamsWithCache(bankName, Math.max(position, 10));

            if (topTeams.size() < position) {
                return "Aucune";
            }

            TeamBalance team = topTeams.get(position - 1);
            return team.getTeamName();
        }

        // Si le joueur n'est pas dans une équipe, renvoyer une valeur par défaut
        if (teamName == null) {
            // Exception pour certains placeholders qui ne nécessitent pas d'équipe
            if (identifier.equals("current_day")) {
                return String.valueOf(plugin.getConfigManager().getCurrentDayNumber());
            }

            if (identifier.equals("team_name")) {
                return "Aucune";
            }

            return "0";
        }

        // Solde de la banque d'équipe: %minefestshop_team_balance_<bankName>%
        if (identifier.startsWith("team_balance_")) {
            String bankName = identifier.substring("team_balance_".length());
            double balance = bankManager.getBankBalance(teamName, bankName);
            return MessageUtil.formatMoney(balance, bankManager.getBankCurrency(bankName));
        }

        // Rang de l'équipe: %minefestshop_team_rank_<bankName>%
        if (identifier.startsWith("team_rank_")) {
            String bankName = identifier.substring("team_rank_".length());
            int rank = bankManager.getTeamRank(teamName, bankName);
            return String.valueOf(rank);
        }

        // Prix d'un item dans le shop principal: %minefestshop_item_price_<itemId>%
        if (identifier.startsWith("item_price_")) {
            String itemId = identifier.substring("item_price_".length());
            double price = plugin.getApi().getDefaultShopItemPrice(itemId);
            return MessageUtil.formatMoney(price, bankManager.getBankCurrency(bankManager.getDefaultBankName()));
        }

        // Prix d'un item dans le shop journalier: %minefestshop_daily_price_<itemId>%
        if (identifier.startsWith("daily_price_")) {
            String itemId = identifier.substring("daily_price_".length());
            double price = plugin.getApi().getDailyShopItemPrice(itemId);
            return MessageUtil.formatMoney(price, bankManager.getBankCurrency(bankManager.getDefaultBankName()));
        }

        // Limite de vente journalière: %minefestshop_daily_limit_<itemId>%
        if (identifier.startsWith("daily_limit_")) {
            String itemId = identifier.substring("daily_limit_".length());
            return String.valueOf(plugin.getShopManager().getDailyItemLimit(itemId));
        }

        // Quantité restante à vendre aujourd'hui: %minefestshop_daily_remaining_<itemId>%
        if (identifier.startsWith("daily_remaining_")) {
            String itemId = identifier.substring("daily_remaining_".length());
            int limit = plugin.getShopManager().getDailyItemLimit(itemId);
            int sold = player.isOnline() ?
                    plugin.getShopManager().getTeamSoldItemCount(teamName, itemId) : 0;
            return String.valueOf(Math.max(0, limit - sold));
        }

        // Contribution du joueur: %minefestshop_contribution_<bankName>%
        if (identifier.startsWith("contribution_")) {
            String bankName = identifier.substring("contribution_".length());
            double contribution = bankManager.getPlayerContribution(player.getUniqueId(), teamName, bankName);
            return MessageUtil.formatMoney(contribution, bankManager.getBankCurrency(bankName));
        }

        // Jour actuel: %minefestshop_current_day%
        if (identifier.equals("current_day")) {
            return String.valueOf(plugin.getConfigManager().getCurrentDayNumber());
        }

        // Meilleur contributeur: %minefestshop_top_contributor_<bankName>%
        if (identifier.startsWith("top_contributor_")) {
            String bankName = identifier.substring("top_contributor_".length());
            BankManager.TopContributor top = bankManager.getTopContributor(teamName, bankName);

            if (top == null) return "Aucun";

            UUID uuid = top.getPlayerUuid();
            OfflinePlayer topPlayer = plugin.getServer().getOfflinePlayer(uuid);
            return topPlayer.getName() != null ? topPlayer.getName() : uuid.toString();
        }

        // Nom de l'équipe: %minefestshop_team_name%
        if (identifier.equals("team_name")) {
            return teamName;
        }

        return null;
    }
}
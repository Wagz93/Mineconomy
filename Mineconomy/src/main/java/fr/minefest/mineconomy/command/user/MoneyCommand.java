package fr.minefest.mineconomy.command.user;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
import fr.minefest.mineconomy.command.CommandRegistry.CommandExecutor;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class MoneyCommand implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;
    
    public MoneyCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BankManager bankManager = plugin.getBankManager();
        
        if (args.length == 0) {
            // Vérifier si le joueur a une équipe
            if (!(sender instanceof Player)) {
                MessageUtil.sendMessage(sender, "error.player_only");
                return true;
            }
            
            Player player = (Player) sender;
            String teamName = bankManager.getPlayerTeam(player);
            
            if (teamName == null) {
                MessageUtil.sendMessage(player, "error.no_team");
                return true;
            }
            
            // Afficher les soldes de toutes les banques de l'équipe
            Map<String, Double> accounts = bankManager.getTeamBankAccounts(teamName);
            
            if (accounts.isEmpty()) {
                MessageUtil.sendMessage(player, "money.no_accounts");
                return true;
            }
            
            MessageUtil.sendMessage(player, "money.header", Map.of("%team%", teamName));
            
            for (Map.Entry<String, Double> entry : accounts.entrySet()) {
                String bankName = entry.getKey();
                double balance = entry.getValue();
                String currency = bankManager.getBankCurrency(bankName);
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%bank%", bankName);
                placeholders.put("%balance%", MessageUtil.formatMoney(balance, currency));
                placeholders.put("%rank%", String.valueOf(bankManager.getTeamRank(teamName, bankName)));
                
                double contribution = bankManager.getPlayerContribution(player.getUniqueId(), teamName, bankName);
                placeholders.put("%contribution%", MessageUtil.formatMoney(contribution, currency));
                
                MessageUtil.sendMessage(player, "money.account_info", placeholders);
            }
            
            return true;
        } else if (args.length == 1) {
            // Vérifier les permissions si on regarde les soldes d'une autre personne
            if (!sender.hasPermission("minefestshop.command.money.others")) {
                MessageUtil.sendMessage(sender, "error.no_permission");
                return true;
            }
            
            // Récupérer le joueur ou l'équipe
            String targetName = args[0];
            
            // Vérifier si c'est une équipe directement
            List<String> teams = plugin.getMinefestAPI().getTeamsList();
            if (teams.contains(targetName)) {
                displayTeamBalance(sender, targetName);
                return true;
            }
            
            // Sinon chercher un joueur
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (!targetPlayer.hasPlayedBefore()) {
                MessageUtil.sendMessage(sender, "error.player_not_found");
                return true;
            }
            
            String teamName = bankManager.getPlayerTeam(targetPlayer);
            if (teamName == null) {
                MessageUtil.sendMessage(sender, "error.player_no_team", Map.of("%player%", targetName));
                return true;
            }
            
            displayTeamBalance(sender, teamName);
            return true;
        }
        
        sendUsage(sender);
        return true;
    }
    
    private void displayTeamBalance(CommandSender sender, String teamName) {
        BankManager bankManager = plugin.getBankManager();
        Map<String, Double> accounts = bankManager.getTeamBankAccounts(teamName);
        
        if (accounts.isEmpty()) {
            MessageUtil.sendMessage(sender, "money.no_accounts_team", Map.of("%team%", teamName));
            return;
        }
        
        MessageUtil.sendMessage(sender, "money.header", Map.of("%team%", teamName));
        
        for (Map.Entry<String, Double> entry : accounts.entrySet()) {
            String bankName = entry.getKey();
            double balance = entry.getValue();
            String currency = bankManager.getBankCurrency(bankName);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bank%", bankName);
            placeholders.put("%balance%", MessageUtil.formatMoney(balance, currency));
            placeholders.put("%rank%", String.valueOf(bankManager.getTeamRank(teamName, bankName)));
            
            MessageUtil.sendMessage(sender, "money.account_info_team", placeholders);
        }
    }
    
    private void sendUsage(CommandSender sender) {
        MessageUtil.sendMessage(sender, "money.usage.header");
        MessageUtil.sendMessage(sender, "money.usage.self");
        
        if (sender.hasPermission("minefestshop.command.money.others")) {
            MessageUtil.sendMessage(sender, "money.usage.others");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!sender.hasPermission("minefestshop.command.money.others")) {
                return Collections.emptyList();
            }
            
            List<String> completions = new ArrayList<>();
            
            // Ajouter les équipes
            completions.addAll(plugin.getMinefestAPI().getTeamsList());
            
            // Ajouter les joueurs en ligne
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
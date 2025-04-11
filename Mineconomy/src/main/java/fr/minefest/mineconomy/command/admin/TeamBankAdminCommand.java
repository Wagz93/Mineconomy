package fr.minefest.mineconomy.command.admin;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.command.CommandRegistry.CommandExecutor;
import fr.minefest.mineconomy.command.CommandRegistry.TabCompleter;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeamBankAdminCommand implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;
    
    public TeamBankAdminCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.bank")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "give":
                return handleGive(sender, args);
            case "set":
                return handleSet(sender, args);
            case "remove":
                return handleRemove(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.bank.create")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "command.tba.create.usage");
            return true;
        }
        
        String bankName = args[1];
        String currencySymbol = args.length > 2 ? args[2] : "€";
        boolean isDefault = args.length > 3 && Boolean.parseBoolean(args[3]);
        
        boolean success = plugin.getBankManager().createBankType(bankName, currencySymbol, isDefault);
        
        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bank%", bankName);
            placeholders.put("%currency%", currencySymbol);
            placeholders.put("%default%", isDefault ? "oui" : "non");
            
            MessageUtil.sendMessage(sender, "command.tba.create.success", placeholders);
        } else {
            MessageUtil.sendMessage(sender, "command.tba.create.failed");
        }
        
        return true;
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.bank.give")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "command.tba.give.usage");
            return true;
        }
        
        String targetName = args[1];
        String bankName = args[2];
        double amount;
        
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "error.invalid_number");
            return true;
        }
        
        if (amount <= 0) {
            MessageUtil.sendMessage(sender, "error.positive_number");
            return true;
        }
        
        // Déterminer si targetName est une équipe ou un joueur
        String teamName = targetName;
        
        // Vérifier d'abord si c'est un joueur
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        if (targetPlayer.hasPlayedBefore()) {
            String playerTeam = plugin.getBankManager().getPlayerTeam(targetPlayer);
            if (playerTeam != null) {
                teamName = playerTeam;
            } else {
                MessageUtil.sendMessage(sender, "error.player_no_team");
                return true;
            }
        }
        
        boolean success = plugin.getBankManager().addBankBalance(teamName, bankName, amount);
        
        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, plugin.getBankManager().getBankCurrency(bankName)));
            
            MessageUtil.sendMessage(sender, "command.tba.give.success", placeholders);
        } else {
            MessageUtil.sendMessage(sender, "command.tba.give.failed");
        }
        
        return true;
    }
    
    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.bank.set")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "command.tba.set.usage");
            return true;
        }
        
        String targetName = args[1];
        String bankName = args[2];
        double amount;
        
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "error.invalid_number");
            return true;
        }
        
        if (amount < 0) {
            MessageUtil.sendMessage(sender, "error.positive_number");
            return true;
        }
        
        // Déterminer si targetName est une équipe ou un joueur
        String teamName = targetName;
        
        // Vérifier d'abord si c'est un joueur
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        if (targetPlayer.hasPlayedBefore()) {
            String playerTeam = plugin.getBankManager().getPlayerTeam(targetPlayer);
            if (playerTeam != null) {
                teamName = playerTeam;
            } else {
                MessageUtil.sendMessage(sender, "error.player_no_team");
                return true;
            }
        }
        
        boolean success = plugin.getBankManager().setBankBalance(teamName, bankName, amount);
        
        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, plugin.getBankManager().getBankCurrency(bankName)));
            
            MessageUtil.sendMessage(sender, "command.tba.set.success", placeholders);
        } else {
            MessageUtil.sendMessage(sender, "command.tba.set.failed");
        }
        
        return true;
    }
    
    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.bank.remove")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "command.tba.remove.usage");
            return true;
        }
        
        String targetName = args[1];
        String bankName = args[2];
        double amount;
        
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "error.invalid_number");
            return true;
        }
        
        if (amount <= 0) {
            MessageUtil.sendMessage(sender, "error.positive_number");
            return true;
        }
        
        // Déterminer si targetName est une équipe ou un joueur
        String teamName = targetName;
        
        // Vérifier d'abord si c'est un joueur
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        if (targetPlayer.hasPlayedBefore()) {
            String playerTeam = plugin.getBankManager().getPlayerTeam(targetPlayer);
            if (playerTeam != null) {
                teamName = playerTeam;
            } else {
                MessageUtil.sendMessage(sender, "error.player_no_team");
                return true;
            }
        }
        
        boolean success = plugin.getBankManager().removeBankBalance(teamName, bankName, amount);
        
        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, plugin.getBankManager().getBankCurrency(bankName)));
            
            MessageUtil.sendMessage(sender, "command.tba.remove.success", placeholders);
        } else {
            MessageUtil.sendMessage(sender, "command.tba.remove.failed");
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "command.tba.help.header");
        MessageUtil.sendMessage(sender, "command.tba.help.create");
        MessageUtil.sendMessage(sender, "command.tba.help.give");
        MessageUtil.sendMessage(sender, "command.tba.help.set");
        MessageUtil.sendMessage(sender, "command.tba.help.remove");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("minefestshop.admin.bank.create")) subCommands.add("create");
            if (sender.hasPermission("minefestshop.admin.bank.give")) subCommands.add("give");
            if (sender.hasPermission("minefestshop.admin.bank.set")) subCommands.add("set");
            if (sender.hasPermission("minefestshop.admin.bank.remove")) subCommands.add("remove");
            
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("give".equals(subCommand) || "set".equals(subCommand) || "remove".equals(subCommand)) {
                // Compléter avec les noms d'équipes ou de joueurs
                List<String> names = new ArrayList<>();
                
                // Ajouter les équipes
                try {
                    names.addAll(plugin.getMinefestAPI().getTeamsList());
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors de la récupération des équipes: " + e.getMessage());
                }
                
                // Ajouter les joueurs en ligne
                names.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
                
                return names.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if ("create".equals(subCommand) || "give".equals(subCommand) || "set".equals(subCommand) || "remove".equals(subCommand)) {
                // Compléter avec les types de banques
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }
}
package fr.minefest.mineconomy.command.bank;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
import fr.minefest.mineconomy.command.BaseCommand;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class TeamBankAdminCommand extends BaseCommand {
    public TeamBankAdminCommand(Mineconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreateCommand(sender, args);
            case "give":
                return handleGiveCommand(sender, args);
            case "set":
                return handleSetCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender, args);
            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "minefestshop.admin.bank.create")) {
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "bank.create_usage");
            return true;
        }

        String bankName = args[1];
        String currencySymbol = args.length > 2 ? args[2] : "€";
        boolean isDefault = args.length > 3 && Boolean.parseBoolean(args[3]);

        BankManager bankManager = plugin.getBankManager();
        boolean success = bankManager.createBankType(bankName, currencySymbol, isDefault);

        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bank%", bankName);
            placeholders.put("%currency%", currencySymbol);
            MessageUtil.sendMessage(sender, "bank.create_success", placeholders);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bank%", bankName);
            MessageUtil.sendMessage(sender, "bank.create_failed", placeholders);
        }

        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "minefestshop.admin.bank.give")) {
            return true;
        }

        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "bank.give_usage");
            return true;
        }

        String targetName = args[1];
        String bankName = args[2];
        double amount;

        try {
            amount = Double.parseDouble(args[3]);
            if (amount <= 0) {
                MessageUtil.sendMessage(sender, "bank.invalid_amount");
                return true;
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "bank.invalid_amount");
            return true;
        }

        // Vérifier si c'est un nom d'équipe ou un joueur
        BankManager bankManager = plugin.getBankManager();
        String teamName = targetName;

        // Si la cible n'est pas une équipe existante, chercher si c'est un joueur
        if (!plugin.getMinefestAPI().getTeamsList().contains(targetName)) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            teamName = bankManager.getPlayerTeam(targetPlayer);

            if (teamName == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%target%", targetName);
                MessageUtil.sendMessage(sender, "bank.target_no_team", placeholders);
                return true;
            }
        }

        boolean success = bankManager.addBankBalance(teamName, bankName, amount);

        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, bankManager.getBankCurrency(bankName)));
            MessageUtil.sendMessage(sender, "bank.give_success", placeholders);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            MessageUtil.sendMessage(sender, "bank.give_failed", placeholders);
        }

        return true;
    }

    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "minefestshop.admin.bank.set")) {
            return true;
        }

        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "bank.set_usage");
            return true;
        }

        String targetName = args[1];
        String bankName = args[2];
        double amount;

        try {
            amount = Double.parseDouble(args[3]);
            if (amount < 0) {
                MessageUtil.sendMessage(sender, "bank.invalid_amount");
                return true;
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "bank.invalid_amount");
            return true;
        }

        // Vérifier si c'est un nom d'équipe ou un joueur
        BankManager bankManager = plugin.getBankManager();
        String teamName = targetName;

        // Si la cible n'est pas une équipe existante, chercher si c'est un joueur
        if (!plugin.getMinefestAPI().getTeamsList().contains(targetName)) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            teamName = bankManager.getPlayerTeam(targetPlayer);

            if (teamName == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%target%", targetName);
                MessageUtil.sendMessage(sender, "bank.target_no_team", placeholders);
                return true;
            }
        }

        boolean success = bankManager.setBankBalance(teamName, bankName, amount);

        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, bankManager.getBankCurrency(bankName)));
            MessageUtil.sendMessage(sender, "bank.set_success", placeholders);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            MessageUtil.sendMessage(sender, "bank.set_failed", placeholders);
        }

        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "minefestshop.admin.bank.remove")) {
            return true;
        }

        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "bank.remove_usage");
            return true;
        }

        String targetName = args[1];
        String bankName = args[2];
        double amount;

        try {
            amount = Double.parseDouble(args[3]);
            if (amount <= 0) {
                MessageUtil.sendMessage(sender, "bank.invalid_amount");
                return true;
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "bank.invalid_amount");
            return true;
        }

        // Vérifier si c'est un nom d'équipe ou un joueur
        BankManager bankManager = plugin.getBankManager();
        String teamName = targetName;

        // Si la cible n'est pas une équipe existante, chercher si c'est un joueur
        if (!plugin.getMinefestAPI().getTeamsList().contains(targetName)) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            teamName = bankManager.getPlayerTeam(targetPlayer);

            if (teamName == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%target%", targetName);
                MessageUtil.sendMessage(sender, "bank.target_no_team", placeholders);
                return true;
            }
        }

        boolean success = bankManager.removeBankBalance(teamName, bankName, amount);

        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            placeholders.put("%amount%", MessageUtil.formatMoney(amount, bankManager.getBankCurrency(bankName)));
            MessageUtil.sendMessage(sender, "bank.remove_success", placeholders);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%team%", teamName);
            placeholders.put("%bank%", bankName);
            MessageUtil.sendMessage(sender, "bank.remove_failed", placeholders);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "bank.help_header");
        MessageUtil.sendMessage(sender, "bank.help_create");
        MessageUtil.sendMessage(sender, "bank.help_give");
        MessageUtil.sendMessage(sender, "bank.help_set");
        MessageUtil.sendMessage(sender, "bank.help_remove");
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "give", "set", "remove").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if ("give".equals(subCommand) || "set".equals(subCommand) || "remove".equals(subCommand)) {
                List<String> suggestions = new ArrayList<>(plugin.getMinefestAPI().getTeamsList());
                
                Bukkit.getOnlinePlayers().forEach(player -> {
                    suggestions.add(player.getName());
                });
                
                return suggestions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if ("create".equals(subCommand) || "give".equals(subCommand) || "set".equals(subCommand) || "remove".equals(subCommand)) {
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
package fr.minefest.mineconomy.command.admin;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.command.CommandRegistry.CommandExecutor;
import fr.minefest.mineconomy.command.CommandRegistry.TabCompleter;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ShopAdminCommand implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;
    
    public ShopAdminCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefestshop.admin")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "setday":
                return handleSetDay(sender, args);
            case "reload":
                return handleReload(sender, args);
            case "resetcontributions":
                return handleResetContributions(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleSetDay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.shop.setday")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "command.shopadmin.setday.usage");
            return true;
        }
        
        int dayNumber;
        
        try {
            dayNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "error.invalid_number");
            return true;
        }
        
        if (dayNumber < 1 || dayNumber > 7) {
            MessageUtil.sendMessage(sender, "error.day_range");
            return true;
        }
        
        plugin.getShopManager().setDay(dayNumber);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%day%", String.valueOf(dayNumber));
        
        MessageUtil.sendMessage(sender, "command.shopadmin.setday.success", placeholders);
        return true;
    }
    
    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.reload")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        plugin.reload();
        MessageUtil.sendMessage(sender, "command.shopadmin.reload.success");
        return true;
    }
    
    private boolean handleResetContributions(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.resetcontributions")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        String bankName = null;
        if (args.length > 1) {
            bankName = args[1];
            
            // Vérifier si la banque existe
            if (!plugin.getBankManager().getAllBankTypes().contains(bankName)) {
                MessageUtil.sendMessage(sender, "error.bank_not_found");
                return true;
            }
        }
        
        plugin.getBankManager().resetContributions(bankName);
        
        if (bankName != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bank%", bankName);
            
            MessageUtil.sendMessage(sender, "command.shopadmin.resetcontributions.success_bank", placeholders);
        } else {
            MessageUtil.sendMessage(sender, "command.shopadmin.resetcontributions.success_all");
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "command.shopadmin.help.header");
        MessageUtil.sendMessage(sender, "command.shopadmin.help.setday");
        MessageUtil.sendMessage(sender, "command.shopadmin.help.reload");
        MessageUtil.sendMessage(sender, "command.shopadmin.help.resetcontributions");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("minefestshop.admin.shop.setday")) subCommands.add("setday");
            if (sender.hasPermission("minefestshop.admin.reload")) subCommands.add("reload");
            if (sender.hasPermission("minefestshop.admin.resetcontributions")) subCommands.add("resetcontributions");
            
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("setday".equals(subCommand)) {
                return Arrays.asList("1", "2", "3", "4", "5", "6", "7");
            } else if ("resetcontributions".equals(subCommand)) {
                // Suggestions de banques
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }
}
package fr.minefest.mineconomy.command;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShopConsoleCommand implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;

    public ShopConsoleCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + label + " <open|reload|setday|resetcontributions> <arguments...>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
                return handleOpenCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "setday":
                return handleSetDayCommand(sender, args);
            case "resetcontributions":
                return handleResetContributionsCommand(sender, args);
            default:
                sender.sendMessage("§cCommande inconnue. Utilisez /" + label + " pour voir l'aide.");
                return true;
        }
    }

    private boolean handleOpenCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /mfshop open <shop|daily> <joueur>");
            return true;
        }

        String shopType = args[1].toLowerCase();
        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cJoueur introuvable ou hors ligne: " + playerName);
            return true;
        }

        if ("shop".equals(shopType)) {
            plugin.getShopManager().openShop(target);
            sender.sendMessage("§aShop principal ouvert pour " + target.getName());
            return true;
        } else if ("daily".equals(shopType)) {
            plugin.getShopManager().openDailyShop(target);
            sender.sendMessage("§aShop journalier ouvert pour " + target.getName());
            return true;
        } else {
            sender.sendMessage("§cType de shop inconnu. Utilisez 'shop' ou 'daily'.");
            return true;
        }
    }

    private boolean handleReloadCommand(CommandSender sender) {
        plugin.reload();
        sender.sendMessage("§aPlugin Mineconomy rechargé avec succès.");
        return true;
    }

    private boolean handleSetDayCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mfshop setday <1-7>");
            return true;
        }

        try {
            int day = Integer.parseInt(args[1]);
            if (day < 1 || day > 7) {
                sender.sendMessage("§cLe jour doit être entre 1 et 7.");
                return true;
            }

            plugin.getShopManager().setDay(day);
            sender.sendMessage("§aShop journalier défini sur le jour " + day + ".");
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cVeuillez entrer un nombre valide entre 1 et 7.");
            return true;
        }
    }

    private boolean handleResetContributionsCommand(CommandSender sender, String[] args) {
        String bankName = args.length > 2 ? args[1] : null;

        plugin.getBankManager().resetContributions(bankName);
        
        if (bankName == null) {
            sender.sendMessage("§aToutes les contributions des joueurs ont été réinitialisées.");
        } else {
            sender.sendMessage("§aLes contributions des joueurs pour la banque '" + bankName + "' ont été réinitialisées.");
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("open", "reload", "setday", "resetcontributions").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if ("open".equals(subCommand)) {
                return Arrays.asList("shop", "daily").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            
            if ("setday".equals(subCommand)) {
                return Arrays.asList("1", "2", "3", "4", "5", "6", "7").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
            
            if ("resetcontributions".equals(subCommand)) {
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && "open".equals(args[0].toLowerCase())) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConsoleCommandManager implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;

    public ConsoleCommandManager(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        
        if ("open".equals(action)) {
            String shopType = args[1].toLowerCase();
            
            if (args.length < 3) {
                sender.sendMessage("§cUtilisation: /mfshop open <shop|daily> <joueur>");
                return true;
            }
            
            String playerName = args[2];
            Player target = Bukkit.getPlayer(playerName);
            
            if (target == null || !target.isOnline()) {
                sender.sendMessage("§cJoueur non trouvé ou hors ligne: " + playerName);
                return true;
            }
            
            if ("shop".equals(shopType)) {
                boolean success = plugin.getShopManager().openShop(target);
                if (success) {
                    sender.sendMessage("§aShop principal ouvert pour " + target.getName());
                } else {
                    sender.sendMessage("§cImpossible d'ouvrir le shop principal pour " + target.getName());
                }
                return true;
            } else if ("daily".equals(shopType)) {
                boolean success = plugin.getShopManager().openDailyShop(target);
                if (success) {
                    sender.sendMessage("§aShop journalier ouvert pour " + target.getName());
                } else {
                    sender.sendMessage("§cImpossible d'ouvrir le shop journalier pour " + target.getName());
                }
                return true;
            }
        } else if ("sync".equals(action)) {
            plugin.getBankManager().syncTeamBanks();
            sender.sendMessage("§aSynchronisation des banques d'équipe effectuée.");
            return true;
        } else if ("info".equals(action)) {
            displayInfo(sender);
            return true;
        }
        
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== Commandes MFShop ===");
        sender.sendMessage("§a/mfshop open shop <joueur> §7- Ouvre le shop principal pour un joueur");
        sender.sendMessage("§a/mfshop open daily <joueur> §7- Ouvre le shop journalier pour un joueur");
        sender.sendMessage("§a/mfshop sync §7- Synchronise les banques d'équipe");
        sender.sendMessage("§a/mfshop info §7- Affiche les informations du plugin");
    }

    private void displayInfo(CommandSender sender) {
        sender.sendMessage("§e=== Mineconomy Info ===");
        sender.sendMessage("§aVersion: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§aBanques: §f" + plugin.getBankManager().getAllBankTypes().size());
        sender.sendMessage("§aItems Shop: §f" + plugin.getShopManager().getShopItems().size());
        sender.sendMessage("§aItems Daily: §f" + plugin.getShopManager().getDailyShopItems().size());
        sender.sendMessage("§aJour Actuel: §f" + plugin.getConfigManager().getCurrentDayNumber());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("open", "sync", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if ("open".equals(args[0].toLowerCase())) {
                return Arrays.asList("shop", "daily").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            if ("open".equals(args[0].toLowerCase())) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }
}
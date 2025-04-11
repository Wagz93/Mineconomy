package fr.minefest.mineconomy.command.admin;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.bank.BankManager;
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

public class BankStatsCommand implements CommandExecutor, TabCompleter {
    private final Mineconomy plugin;
    
    public BankStatsCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefestshop.admin.stats")) {
            MessageUtil.sendMessage(sender, "error.no_permission");
            return true;
        }
        
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        BankManager bankManager = plugin.getBankManager();
        
        switch (args[0].toLowerCase()) {
            case "top":
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, "command.bankstats.top.usage");
                    return true;
                }
                
                String bankName = args[1];
                int limit;
                
                try {
                    limit = Integer.parseInt(args[2]);
                    if (limit <= 0) {
                        MessageUtil.sendMessage(sender, "error.positive_number");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, "error.invalid_number");
                    return true;
                }
                
                if (!bankManager.getAllBankTypes().contains(bankName)) {
                    MessageUtil.sendMessage(sender, "error.bank_not_found");
                    return true;
                }
                
                List<BankManager.TeamBalance> topTeams = bankManager.getTopTeams(bankName, limit);
                
                if (topTeams.isEmpty()) {
                    MessageUtil.sendMessage(sender, "command.bankstats.top.no_data");
                    return true;
                }
                
                String currency = bankManager.getBankCurrency(bankName);
                MessageUtil.sendMessage(sender, "command.bankstats.top.header", Map.of("%bank%", bankName));
                
                int rank = 1;
                for (BankManager.TeamBalance team : topTeams) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%rank%", String.valueOf(rank++));
                    placeholders.put("%team%", team.getTeamName());
                    placeholders.put("%balance%", MessageUtil.formatMoney(team.getBalance(), currency));
                    
                    MessageUtil.sendMessage(sender, "command.bankstats.top.entry", placeholders);
                }
                
                return true;
                
            case "contributors":
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, "command.bankstats.contributors.usage");
                    return true;
                }
                
                String team = args[1];
                bankName = args[2];
                
                if (!bankManager.getAllBankTypes().contains(bankName)) {
                    MessageUtil.sendMessage(sender, "error.bank_not_found");
                    return true;
                }
                
                // Vérifier si l'équipe existe
                if (!plugin.getMinefestAPI().getTeamsList().contains(team)) {
                    MessageUtil.sendMessage(sender, "error.team_not_found");
                    return true;
                }
                
                // Récupérer le top contributeur
                BankManager.TopContributor topContributor = bankManager.getTopContributor(team, bankName);
                
                if (topContributor == null) {
                    MessageUtil.sendMessage(sender, "command.bankstats.contributors.no_data");
                    return true;
                }
                
                OfflinePlayer topPlayer = Bukkit.getOfflinePlayer(topContributor.getPlayerUuid());
                String playerName = topPlayer.getName() != null ? topPlayer.getName() : topContributor.getPlayerUuid().toString();
                
                currency = bankManager.getBankCurrency(bankName);
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%team%", team);
                placeholders.put("%bank%", bankName);
                placeholders.put("%player%", playerName);
                placeholders.put("%amount%", MessageUtil.formatMoney(topContributor.getAmount(), currency));
                
                MessageUtil.sendMessage(sender, "command.bankstats.contributors.top", placeholders);
                return true;
                
            case "rank":
                if (args.length < 2) {
                    MessageUtil.sendMessage(sender, "command.bankstats.rank.usage");
                    return true;
                }
                
                bankName = args[1];
                
                if (!bankManager.getAllBankTypes().contains(bankName)) {
                    MessageUtil.sendMessage(sender, "error.bank_not_found");
                    return true;
                }
                
                List<BankManager.TeamBalance> allTeams = bankManager.getTopTeams(bankName, 100); // Limite haute
                
                if (allTeams.isEmpty()) {
                    MessageUtil.sendMessage(sender, "command.bankstats.rank.no_data");
                    return true;
                }
                
                currency = bankManager.getBankCurrency(bankName);
                MessageUtil.sendMessage(sender, "command.bankstats.rank.header", Map.of("%bank%", bankName));
                
                rank = 1;
                for (BankManager.TeamBalance teamData : allTeams) {
                    placeholders = new HashMap<>();
                    placeholders.put("%rank%", String.valueOf(rank++));
                    placeholders.put("%team%", teamData.getTeamName());
                    placeholders.put("%balance%", MessageUtil.formatMoney(teamData.getBalance(), currency));
                    
                    MessageUtil.sendMessage(sender, "command.bankstats.rank.entry", placeholders);
                    
                    // Limiter l'affichage pour ne pas spammer le chat
                    if (rank > 10) {
                        MessageUtil.sendMessage(sender, "command.bankstats.rank.more", 
                                Map.of("%count%", String.valueOf(allTeams.size() - 10)));
                        break;
                    }
                }
                
                return true;
                
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "command.bankstats.help.header");
        MessageUtil.sendMessage(sender, "command.bankstats.help.top");
        MessageUtil.sendMessage(sender, "command.bankstats.help.contributors");
        MessageUtil.sendMessage(sender, "command.bankstats.help.rank");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("top", "contributors", "rank").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if ("top".equals(subCommand) || "rank".equals(subCommand)) {
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if ("contributors".equals(subCommand)) {
                return plugin.getMinefestAPI().getTeamsList().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if ("top".equals(subCommand)) {
                return Arrays.asList("5", "10", "20", "50", "100").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            } else if ("contributors".equals(subCommand)) {
                return plugin.getBankManager().getAllBankTypes().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }
}
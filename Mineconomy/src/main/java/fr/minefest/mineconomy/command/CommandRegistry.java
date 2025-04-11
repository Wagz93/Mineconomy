package fr.minefest.mineconomy.command;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.command.admin.BankStatsCommand;
import fr.minefest.mineconomy.command.admin.ShopAdminCommand;
import fr.minefest.mineconomy.command.admin.TeamBankAdminCommand;
import fr.minefest.mineconomy.command.user.DailyShopCommand;
import fr.minefest.mineconomy.command.user.MoneyCommand;
import fr.minefest.mineconomy.command.user.ShopCommand;
import fr.minefest.mineconomy.util.SplashManager;
import org.bukkit.command.PluginCommand;

public class CommandRegistry {
    private final Mineconomy plugin;
    
    public CommandRegistry(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    public void registerCommands() {
        // Commandes utilisateur
        registerCommand("shop", new ShopCommand(plugin));
        registerCommand("daily", new DailyShopCommand(plugin));
        registerCommand("money", new MoneyCommand(plugin));
        
        // Commandes admin
        registerCommand("tba", new TeamBankAdminCommand(plugin));
        registerCommand("shopadmin", new ShopAdminCommand(plugin));
        registerCommand("bankstats", new BankStatsCommand(plugin));
        
        // Afficher le splash à la console
        SplashManager.showSplash(plugin);
    }
    
    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
        } else {
            plugin.getLogger().warning("La commande '" + name + "' n'est pas enregistrée dans plugin.yml");
        }
    }
    
    public interface CommandExecutor extends org.bukkit.command.CommandExecutor {}
    
    public interface TabCompleter extends org.bukkit.command.TabCompleter {}
}
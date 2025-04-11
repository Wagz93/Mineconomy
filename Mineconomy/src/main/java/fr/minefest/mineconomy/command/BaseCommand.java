package fr.minefest.mineconomy.command;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final Mineconomy plugin;

    public BaseCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return execute(sender, command, label, args);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de l'exécution de la commande '" + label + "': " + e.getMessage());
            e.printStackTrace();
            MessageUtil.sendMessage(sender, "error.command_error");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(sender, command, alias, args);
    }

    protected abstract boolean execute(CommandSender sender, Command command, String label, String[] args);

    protected List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }

    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    protected Player asPlayer(CommandSender sender) {
        return (Player) sender;
    }

    protected boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        
        MessageUtil.sendMessage(sender, "error.no_permission");
        return false;
    }
}
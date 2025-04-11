package fr.minefest.mineconomy.command.user;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.command.CommandRegistry.CommandExecutor;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DailyShopCommand implements CommandExecutor {
    private final Mineconomy plugin;
    
    public DailyShopCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "error.player_only");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("minefestshop.user.daily")) {
            MessageUtil.sendMessage(player, "error.no_permission");
            return true;
        }
        
        plugin.getShopManager().openDailyShop(player);
        return true;
    }
}
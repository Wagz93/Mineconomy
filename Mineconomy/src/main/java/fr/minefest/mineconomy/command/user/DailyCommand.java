package fr.minefest.mineconomy.command.user;

import fr.minefest.mineconomy.Mineconomy;
import fr.minefest.mineconomy.command.BaseCommand;
import fr.minefest.mineconomy.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class DailyCommand extends BaseCommand {
    public DailyCommand(Mineconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!isPlayer(sender)) {
            MessageUtil.sendMessage(sender, "error.player_only");
            return true;
        }

        if (!checkPermission(sender, "minefestshop.user.daily")) {
            return true;
        }

        plugin.getShopManager().openDailyShop(asPlayer(sender));
        return true;
    }
}
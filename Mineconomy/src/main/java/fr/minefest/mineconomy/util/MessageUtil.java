package fr.minefest.mineconomy.util;

import fr.minefest.mineconomy.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static FileConfiguration messages;
    private static String prefix;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final Map<String, String> messageCache = new HashMap<>();

    public static void initialize(ConfigManager configManager) {
        messages = configManager.getConfig(ConfigManager.ConfigType.MESSAGES);
        prefix = translate(messages.getString("prefix", "&8[&bMinefestShop&8] "));
        messageCache.clear();
    }

    public static String translate(String message) {
        if (message == null) return "";
        
        // Remplacer les codes hexadécimaux par leurs codes de formatage Minecraft
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = String.format("\u00A7x\u00A7%c\u00A7%c\u00A7%c\u00A7%c\u00A7%c\u00A7%c",
                    hexColor.charAt(0), hexColor.charAt(1), hexColor.charAt(2),
                    hexColor.charAt(3), hexColor.charAt(4), hexColor.charAt(5));
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        
        // Remplacer les codes de couleur standard
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String getMessage(String path) {
        return messageCache.computeIfAbsent(path, p -> {
            String message = messages.getString(p);
            if (message == null) {
                return prefix + "&cMessage manquant: " + p;
            }
            return prefix + translate(message);
        });
    }

    public static String getMessageNoPrefix(String path) {
        return messageCache.computeIfAbsent("noprefix." + path, p -> {
            String message = messages.getString(path.substring(9));
            if (message == null) {
                return "&cMessage manquant: " + path;
            }
            return translate(message);
        });
    }

    public static String formatMoney(double amount) {
        return MONEY_FORMAT.format(amount);
    }

    public static String formatMoney(double amount, String currencySymbol) {
        return formatMoney(amount) + " " + currencySymbol;
    }

    public static void sendMessage(CommandSender sender, String path) {
        sender.sendMessage(getMessage(path));
    }

    public static void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        
        sender.sendMessage(message);
    }

    public static void sendMessageNoPrefix(CommandSender sender, String path) {
        sender.sendMessage(getMessageNoPrefix(path));
    }

    public static void sendTitle(Player player, String titlePath, String subtitlePath, int fadeIn, int stay, int fadeOut) {
        String title = getMessageNoPrefix(titlePath);
        String subtitle = getMessageNoPrefix(subtitlePath);
        
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static String getGuiTitle(String path) {
        String title = messages.getString("gui." + path);
        if (title == null) {
            return translate("&cGUI Titre manquant: " + path);
        }
        return translate(title);
    }
}
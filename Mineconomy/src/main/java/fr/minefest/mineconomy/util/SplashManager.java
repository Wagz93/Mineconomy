package fr.minefest.mineconomy.util;

import fr.minefest.mineconomy.Mineconomy;
import org.bukkit.ChatColor;

public class SplashManager {

    // Art ASCII : "MINEFEST" et "ECONOMY" en style Pixel/Block révisé
    private static final String[] SPLASH = {
            // --- MINEFEST (Block Style - Validé) ---
            "███╗   ███╗  ██╗  ███╗   ██╗  ███████╗  ███████╗  ███████╗  ███████╗  ████████╗",
            "████╗ ████║  ██║  ████╗  ██║  ██╔════╝  ██╔════╝  ██╔════╝  ██╔════╝  ╚══██╔══╝",
            "██╔████╔██║  ██║  ██╔██╗ ██║  ███████╗  ███████╗  ███████╗  ███████╗     ██║   ",
            "██║╚██╔╝██║  ██║  ██║╚██╗██║  ██╔════╝  ██╔════╝  ██╔════╝  ╚════██║     ██║   ",
            "██║ ╚═╝ ██║  ██║  ██║ ╚████║  ███████║  ██║       ███████║  ███████║     ██║   ",
            "╚═╝     ╚═╝  ╚═╝  ╚═╝  ╚═══╝  ╚══════╝  ╚═╝       ╚══════╝  ╚══════╝     ╚═╝   ",
            "                                                                               ",
            "                                                                               ",
            // --- ECONOMY (Block Style - Révisé pour être comme en haut) ---
            "███████╗  ███████╗    █████ ╗  ███╗   ██╗   █████ ╗   ███╗   ███╗  ██╗   ██╗",
            "██╔════╝  ██╔════╝  ██╔═══██╗  ████╗  ██║  ██╔═══██╗  ████╗ ████║  ╚██╗ ██╔╝",
            "███████╗  ██║       ██║   ██║  ██╔██╗ ██║  ██║   ██║  ██╔████╔██║   ╚████╔╝",
            "██╔════╝  ██╚════╝  ██║   ██║  ██║╚██╗██║  ██║   ██║  ██║╚██╔╝██║     ██╔╝ ",
            "███████║  ███████║  ██╚═══██║  ██║ ╚████║  ██╚═══██║  ██║ ╚═╝ ██║     ██║  ",
            "╚══════╝   ═════╝    ██████║   ╚═╝  ╚═══╝   █████║    ╚═╝     ╚═╝     ╚═╝  "
    };

    public static void showSplash(Mineconomy plugin) {
        plugin.getLogger().info(""); // Ligne vide avant
        for (String line : SPLASH) {
            plugin.getLogger().info(ChatColor.AQUA + line);
        }
        plugin.getLogger().info(""); // Ligne vide après l'art
        plugin.getLogger().info(ChatColor.GREEN + "Version: " + plugin.getDescription().getVersion());
        plugin.getLogger().info(ChatColor.GREEN + "Auteur: " + String.join(", ", plugin.getDescription().getAuthors())); // Gère plusieurs auteurs
        plugin.getLogger().info(""); // Ligne vide finale
    }
}
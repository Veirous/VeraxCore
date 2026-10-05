package com.verax.veraxcore.utils;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    public static final int RESOURCE_ID = 138126;
    private final VeraxCore plugin;
    private static String cachedLatestVersion = null;
    private static boolean updateAvailable = false;

    public UpdateChecker(VeraxCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks for updates asynchronously via Spiget API & SpigotMC API.
     */
    public void checkForUpdates(Consumer<String> consumer) {
        if (!plugin.getConfig().getBoolean("check-updates", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            String latestVersion = fetchFromSpiget();
            if (latestVersion == null || latestVersion.isEmpty()) {
                latestVersion = fetchFromSpigotLegacy();
            }

            if (latestVersion != null && !latestVersion.trim().isEmpty()) {
                cachedLatestVersion = latestVersion.trim();
                String currentVersion = plugin.getDescription().getVersion();
                updateAvailable = isNewerVersion(currentVersion, cachedLatestVersion);

                final String finalVersion = cachedLatestVersion;
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (consumer != null) {
                        consumer.accept(finalVersion);
                    }
                });
            }
        });
    }

    private String fetchFromSpiget() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "https://api.spiget.org/v2/resources/" + RESOURCE_ID + "/versions/latest"
            ).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "VeraxCore-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                Pattern pattern = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(response.toString());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            plugin.logDebug("Spiget check failed: " + e.getMessage());
        }
        return null;
    }

    private String fetchFromSpigotLegacy() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID
            ).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) VeraxCore");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            plugin.logDebug("Spigot legacy check failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Semantic version comparison:
     * Returns true only if the current version is strictly older than latestVersion.
     */
    public static boolean isNewerVersion(String currentVersion, String latestVersion) {
        if (currentVersion == null || latestVersion == null) return false;
        try {
            String v1 = currentVersion.replaceAll("[^0-9.]", "").trim();
            String v2 = latestVersion.replaceAll("[^0-9.]", "").trim();

            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");

            int length = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < length; i++) {
                int num1 = i < parts1.length && !parts1[i].isEmpty() ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length && !parts2[i].isEmpty() ? Integer.parseInt(parts2[i]) : 0;

                if (num2 > num1) {
                    return true; // Remote version is newer
                } else if (num2 < num1) {
                    return false; // Current version is already newer (e.g. dev build)
                }
            }
            return false; // Versions are identical
        } catch (Exception e) {
            return !currentVersion.equalsIgnoreCase(latestVersion);
        }
    }

    /**
     * Prints update status and download details to console.
     */
    public void logToConsole() {
        checkForUpdates(latestVersion -> {
            String currentVersion = plugin.getDescription().getVersion();
            displayInfoMessage(plugin.getServer().getConsoleSender(), currentVersion, latestVersion != null ? latestVersion : currentVersion);
        });
    }

    /**
     * Notifies staff/OP members about update status when they join.
     */
    public static void notifyPlayerIfUpdateAvailable(Player player, VeraxCore plugin) {
        if (!plugin.getConfig().getBoolean("check-updates", true)) {
            return;
        }

        if (!player.hasPermission("veraxcore.admin") && !player.isOp()) {
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();

        if (cachedLatestVersion != null) {
            if (updateAvailable) {
                displayInfoMessage(player, currentVersion, cachedLatestVersion);
            }
        } else {
            // If not cached yet, check and send
            new UpdateChecker(plugin).checkForUpdates(latestVersion -> {
                if (player.isOnline() && updateAvailable) {
                    displayInfoMessage(player, currentVersion, latestVersion);
                }
            });
        }
    }

    /**
     * Displays formatted plugin & update banner to the specified sender (Console or Player).
     */
    public static void displayInfoMessage(CommandSender sender, String currentVersion, String latestVersion) {
        String resourceLink = "https://www.spigotmc.org/resources/veraxcore." + RESOURCE_ID + "/";
        boolean hasUpdate = isNewerVersion(currentVersion, latestVersion);

        sender.sendMessage(ChatColor.DARK_GRAY + "================================================================");
        sender.sendMessage(ChatColor.AQUA + " __      __ ______ _____         __   __");
        sender.sendMessage(ChatColor.AQUA + " \\ \\    / /|  ____|  __ \\ /\\     \\ \\ / /");
        sender.sendMessage(ChatColor.AQUA + "  \\ \\  / / | |__  | |__) /  \\     \\ V / ");
        sender.sendMessage(ChatColor.AQUA + "   \\ \\/ /  |  __| |  _  / /\\ \\     > <  ");
        sender.sendMessage(ChatColor.AQUA + "    \\  /   | |____| | \\ \\ ____ \\  / . \\ ");
        sender.sendMessage(ChatColor.AQUA + "     \\/    |______|_|  \\_/_/  \\_//_/ \\_\\");
        sender.sendMessage(ChatColor.DARK_GRAY + "================================================================");
        sender.sendMessage(ChatColor.YELLOW + "VeraxCore Plugin Information");
        sender.sendMessage(ChatColor.GRAY + "Running Version: " + (hasUpdate ? ChatColor.RED : ChatColor.GREEN) + currentVersion);
        sender.sendMessage(ChatColor.GRAY + "Latest Version: " + ChatColor.GREEN + latestVersion);
        if (hasUpdate) {
            sender.sendMessage(ChatColor.GOLD + "Download: " + ChatColor.AQUA + resourceLink);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Status: " + ChatColor.GREEN + "You are running the latest version!");
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "================================================================");
    }

    public void displayInfoMessage(String currentVersion, String latestVersion) {
        displayInfoMessage(this.plugin.getServer().getConsoleSender(), currentVersion, latestVersion);
    }

    public static String getCachedLatestVersion() {
        return cachedLatestVersion;
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }
}

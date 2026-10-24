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
     * Starts the update checker: runs startup check and sets up a periodic 6-hour check.
     */
    public void start() {
        logToConsole();

        // Check periodically every 6 hours (432,000 ticks)
        long periodTicks = 20L * 60 * 60 * 6;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkForUpdates(latestVersion -> {
                String currentVersion = plugin.getDescription().getVersion();
                if (isNewerVersion(currentVersion, latestVersion)) {
                    displayConsoleBanner(currentVersion, latestVersion);
                    notifyOnlineOps(plugin, currentVersion, latestVersion);
                }
            });
        }, periodTicks, periodTicks);
    }

    /**
     * Prints update status and download details to console, and alerts online OPs if an update is found.
     */
    public void logToConsole() {
        checkForUpdates(latestVersion -> {
            String currentVersion = plugin.getDescription().getVersion();
            boolean hasUpdate = isNewerVersion(currentVersion, latestVersion);

            // Display to console
            displayConsoleBanner(currentVersion, latestVersion != null ? latestVersion : currentVersion);

            // If a new update is found, also notify online OPs/Admins immediately!
            if (hasUpdate) {
                notifyOnlineOps(plugin, currentVersion, latestVersion);
            }
        });
    }

    /**
     * Notifies all online OP / admin players ONLY if a newer version is available.
     * If they are on the latest version, sends nothing.
     */
    public static void notifyOnlineOps(VeraxCore plugin, String currentVersion, String latestVersion) {
        if (plugin == null || !plugin.getConfig().getBoolean("check-updates", true)) {
            return;
        }
        if (!isNewerVersion(currentVersion, latestVersion)) {
            return; // En son sürümdelerse hiçbir şey yazma!
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp() || p.hasPermission("veraxcore.admin")) {
                sendPlayerUpdateNotice(p, plugin, currentVersion, latestVersion);
            }
        }
    }

    /**
     * Notifies an OP/admin player when they join ONLY if a newer version is available.
     * If they are on the latest version, sends nothing.
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
            // SADECE ve SADECE yeni bir sürüm varsa OP'ye yaz; en son sürümdeyse hiçbir şey yazma!
            if (updateAvailable && isNewerVersion(currentVersion, cachedLatestVersion)) {
                sendPlayerUpdateNotice(player, plugin, currentVersion, cachedLatestVersion);
            }
        } else {
            // Henüz önbellekte yoksa kontrol et; yalnızca yeni sürüm varsa yaz
            new UpdateChecker(plugin).checkForUpdates(latestVersion -> {
                if (player.isOnline() && isNewerVersion(currentVersion, latestVersion)) {
                    sendPlayerUpdateNotice(player, plugin, currentVersion, latestVersion);
                }
            });
        }
    }

    /**
     * Sends a clean, sleek in-game update notification designed for chat to an OP player.
     */
    public static void sendPlayerUpdateNotice(Player player, VeraxCore plugin, String currentVersion, String latestVersion) {
        if (player == null || !player.isOnline()) return;

        String resourceLink = "https://www.spigotmc.org/resources/veraxcore." + RESOURCE_ID + "/";
        String lang = (plugin != null && plugin.getLanguageManager() != null)
                ? plugin.getLanguageManager().getCurrentLanguage() : "tr";

        player.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
        if ("tr".equalsIgnoreCase(lang)) {
            player.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &#E74C3CYeni Güncelleme Mevcut! &7(&av" + latestVersion + "&7)"));
            player.sendMessage(plugin.translateHexColorCodes(""));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Mevcut Sürüm: &c" + currentVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• En Son Sürüm: &#2ECC71" + latestVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• İndirme Adresi: &#1BA1FF" + resourceLink));
        } else if ("de".equalsIgnoreCase(lang)) {
            player.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &#E74C3CNeues Update verfügbar! &7(&av" + latestVersion + "&7)"));
            player.sendMessage(plugin.translateHexColorCodes(""));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Aktuelle Version: &c" + currentVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Neueste Version: &#2ECC71" + latestVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Download-Link: &#1BA1FF" + resourceLink));
        } else {
            player.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &#E74C3CNew Update Available! &7(&av" + latestVersion + "&7)"));
            player.sendMessage(plugin.translateHexColorCodes(""));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Current Version: &c" + currentVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Latest Version: &#2ECC71" + latestVersion));
            player.sendMessage(plugin.translateHexColorCodes("  &7• Download Link: &#1BA1FF" + resourceLink));
        }
        player.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
    }

    /**
     * Displays update banner to Console or Player.
     * If sender is a Player and already on the latest version, nothing is sent.
     */
    public static void displayInfoMessage(CommandSender sender, String currentVersion, String latestVersion) {
        if (sender instanceof Player) {
            // Player ise: Yalnızca güncelleme varsa bildir, en son sürümdeyse HİÇBİR ŞEY yazma!
            if (isNewerVersion(currentVersion, latestVersion)) {
                VeraxCore pl = (VeraxCore) Bukkit.getPluginManager().getPlugin("VeraxCore");
                sendPlayerUpdateNotice((Player) sender, pl, currentVersion, latestVersion);
            }
            return;
        }

        displayConsoleBanner(currentVersion, latestVersion);
    }

    /**
     * Formatted console banner for the console sender.
     */
    private static void displayConsoleBanner(String currentVersion, String latestVersion) {
        String resourceLink = "https://www.spigotmc.org/resources/veraxcore." + RESOURCE_ID + "/";
        boolean hasUpdate = isNewerVersion(currentVersion, latestVersion);

        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(ChatColor.DARK_GRAY + "============================================================================");
        console.sendMessage(ChatColor.AQUA + "  ██╗   ██╗███████╗██████╗  █████╗ ██╗  ██╗ ██████╗ ██████╗ ██████╗ ███████╗");
        console.sendMessage(ChatColor.AQUA + "  ██║   ██║██╔════╝██╔══██╗██╔══██╗╚██╗██╔╝██╔════╝██╔═══██╗██╔══██╗██╔════╝");
        console.sendMessage(ChatColor.AQUA + "  ██║   ██║█████╗  ██████╔╝███████║ ╚███╔╝ ██║     ██║   ██║██████╔╝█████╗  ");
        console.sendMessage(ChatColor.AQUA + "  ╚██╗ ██╔╝██╔══╝  ██╔══██╗██╔══██║ ██╔██╗ ██║     ██║   ██║██╔══██╗██╔══╝  ");
        console.sendMessage(ChatColor.AQUA + "   ╚████╔╝ ███████╗██║  ██║██║  ██║██╔╝ ██╗╚██████╗╚██████╔╝██║  ██║███████╗");
        console.sendMessage(ChatColor.AQUA + "    ╚═══╝  ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝");
        console.sendMessage(ChatColor.DARK_GRAY + "============================================================================");
        console.sendMessage(ChatColor.YELLOW + "VeraxCore Plugin Information");
        console.sendMessage(ChatColor.GRAY + "Running Version: " + (hasUpdate ? ChatColor.RED : ChatColor.GREEN) + currentVersion);
        console.sendMessage(ChatColor.GRAY + "Latest Version: " + ChatColor.GREEN + latestVersion);
        if (hasUpdate) {
            console.sendMessage(ChatColor.GOLD + "Download: " + ChatColor.AQUA + resourceLink);
        } else {
            console.sendMessage(ChatColor.GREEN + "Status: " + ChatColor.GREEN + "You are running the latest version!");
        }
        console.sendMessage(ChatColor.DARK_GRAY + "================================================================");
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

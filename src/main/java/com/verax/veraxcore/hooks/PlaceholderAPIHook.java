package com.verax.veraxcore.hooks;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.commands.StaffChatCommand;
import com.verax.veraxcore.commands.StaffPlaytimeCommand;
import com.verax.veraxcore.listeners.ActivityListener;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final VeraxCore plugin;

    // Fast in-memory cache for database seconds (3 seconds TTL)
    // Prevents spamming the database from high-frequency scoreboard/TAB polls
    private static class CacheEntry {
        final int seconds;
        final long timestamp;

        CacheEntry(int seconds, long timestamp) {
            this.seconds = seconds;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, CacheEntry> secondsCache = new ConcurrentHashMap<>();

    public PlaceholderAPIHook(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public static void registerHook(VeraxCore plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new PlaceholderAPIHook(plugin).register();
                plugin.getLogger().info("[Hooks] PlaceholderAPI expansion successfully registered!");
            } catch (Throwable t) {
                plugin.getLogger().warning("[Hooks] Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
    }

    public static String formatWithPAPI(@Nullable Player player, @NotNull String text) {
        if (text.isEmpty() || player == null) return text;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }
        return text;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "veraxcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "VeraxDev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    private int getPlayerPlaytimeSeconds(Player player, String type) {
        if (player == null) return 0;
        UUID uuid = player.getUniqueId();
        String cacheKey = uuid.toString() + ":" + type.toLowerCase(Locale.ROOT);

        long now = System.currentTimeMillis();
        CacheEntry entry = secondsCache.get(cacheKey);
        int dbSeconds;

        if (entry != null && (now - entry.timestamp) < 3000L) {
            dbSeconds = entry.seconds;
        } else {
            dbSeconds = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getSeconds(uuid, type) : 0;
            secondsCache.put(cacheKey, new CacheEntry(dbSeconds, now));
        }

        int sessionSeconds = 0;
        if (ActivityListener.loginTimes.containsKey(uuid) && !ActivityListener.isEssentialsAfkStatic(player)) {
            long joinTime = ActivityListener.loginTimes.get(uuid);
            sessionSeconds = (int) ((now - joinTime) / 1000);
            if (sessionSeconds < 0) sessionSeconds = 0;
        }

        return dbSeconds + sessionSeconds;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        String param = params.toLowerCase(Locale.ROOT);

        // --- GLOBAL PLACEHOLDERS (DO NOT REQUIRE PLAYER) ---

        // Maintenance Status
        if (param.equals("maintenance")) {
            return plugin.getMaintenanceManager() != null && plugin.getMaintenanceManager().isMaintenanceActive() ? "true" : "false";
        }
        if (param.equals("maintenance_status")) {
            return plugin.getMaintenanceManager() != null && plugin.getMaintenanceManager().isMaintenanceActive() ? "Active" : "Inactive";
        }
        if (param.equals("maintenance_time")) {
            if (plugin.getMaintenanceManager() != null) {
                if (plugin.getMaintenanceManager().isMaintenanceActive()) {
                    long elapsed = Math.max(0, (System.currentTimeMillis() - plugin.getConfig().getLong("maintenance.start-time", 0L)) / 1000L);
                    return plugin.getMaintenanceManager().formatTime(elapsed);
                } else if (plugin.getMaintenanceManager().isCountdownActive()) {
                    return plugin.getMaintenanceManager().formatTime(plugin.getMaintenanceManager().getCountdownRemainingSeconds());
                }
            }
            return "0";
        }

        // Chat Status
        if (param.equals("chat_status")) {
            return plugin.getChatControlListener() != null && !plugin.getChatControlListener().isChatOpen() ? "Locked" : "Open";
        }
        if (param.equals("chat_open")) {
            return plugin.getChatControlListener() != null && plugin.getChatControlListener().isChatOpen() ? "true" : "false";
        }

        // --- PLAYER SPECIFIC PLACEHOLDERS ---
        if (player == null) {
            return "";
        }

        // Staff Playtime & Quota
        if (param.equals("stafftime_daily")) {
            int secs = getPlayerPlaytimeSeconds(player, "daily");
            return StaffPlaytimeCommand.formatPlaytime(secs, plugin);
        }
        if (param.equals("stafftime_daily_seconds")) {
            return String.valueOf(getPlayerPlaytimeSeconds(player, "daily"));
        }
        if (param.equals("stafftime_weekly")) {
            int secs = getPlayerPlaytimeSeconds(player, "weekly");
            return StaffPlaytimeCommand.formatPlaytime(secs, plugin);
        }
        if (param.equals("stafftime_weekly_seconds")) {
            return String.valueOf(getPlayerPlaytimeSeconds(player, "weekly"));
        }
        if (param.equals("stafftime_monthly")) {
            int secs = getPlayerPlaytimeSeconds(player, "monthly");
            return StaffPlaytimeCommand.formatPlaytime(secs, plugin);
        }
        if (param.equals("stafftime_monthly_seconds")) {
            return String.valueOf(getPlayerPlaytimeSeconds(player, "monthly"));
        }
        if (param.equals("stafftime_total")) {
            int secs = getPlayerPlaytimeSeconds(player, "total");
            return StaffPlaytimeCommand.formatPlaytime(secs, plugin);
        }
        if (param.equals("stafftime_total_seconds")) {
            return String.valueOf(getPlayerPlaytimeSeconds(player, "total"));
        }

        // Staff Quota (Daily Target)
        int targetSeconds = plugin.getConfig().getInt("target-seconds", plugin.getConfig().getInt("hedef-saniye", 7200));
        if (param.equals("stafftime_quota")) {
            return StaffPlaytimeCommand.formatPlaytime(targetSeconds, plugin);
        }
        if (param.equals("stafftime_quota_seconds")) {
            return String.valueOf(targetSeconds);
        }
        if (param.equals("stafftime_quota_met")) {
            int dailySecs = getPlayerPlaytimeSeconds(player, "daily");
            return dailySecs >= targetSeconds ? "yes" : "no";
        }
        if (param.equals("stafftime_quota_icon")) {
            int dailySecs = getPlayerPlaytimeSeconds(player, "daily");
            return dailySecs >= targetSeconds ? "✔" : "✘";
        }

        // Staff Status
        if (param.equals("staff_is_staff")) {
            return ActivityListener.hasStaffRankStatic(plugin, player) ? "true" : "false";
        }
        if (param.equals("staff_is_afk")) {
            return ActivityListener.isEssentialsAfkStatic(player) ? "true" : "false";
        }
        if (param.equals("staffchat_status") || param.equals("staffchat_toggled")) {
            return StaffChatCommand.isStaffChatToggled(player.getUniqueId()) ? "true" : "false";
        }

        // Player Vault (PV)
        if (param.equals("pv_amount") || param.equals("pv_max")) {
            return plugin.getPvManager() != null ? String.valueOf(plugin.getPvManager().getMaxVaults(player)) : "0";
        }
        if (param.equals("pv_slots")) {
            return plugin.getPvManager() != null ? String.valueOf(plugin.getPvManager().getVaultSlotSize(player)) : "0";
        }

        // Player Settings
        if (param.equals("trade_enabled")) {
            return plugin.getPlayerSettingsManager() != null ? String.valueOf(plugin.getPlayerSettingsManager().isTradeEnabled(player.getUniqueId())) : "true";
        }
        if (param.equals("autoannouncement_enabled")) {
            return plugin.getPlayerSettingsManager() != null ? String.valueOf(plugin.getPlayerSettingsManager().isAutoAnnouncementEnabled(player.getUniqueId())) : "true";
        }

        return null;
    }
}

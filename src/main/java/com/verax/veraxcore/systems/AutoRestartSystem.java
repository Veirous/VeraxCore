package com.verax.veraxcore.systems;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoRestartSystem implements Listener {

    private final VeraxCore plugin;
    private BukkitTask timerTask = null;
    private BossBar restartBossBar = null;
    private boolean enabled = false;
    private List<LocalTime> restartTimes = new ArrayList<>();
    private long leadTimeSeconds = 1800L; // Default 30 minutes (1800 seconds)
    private String restartCommand = "restart";
    private BarColor cachedBarColor = BarColor.YELLOW;
    private BarStyle cachedBarStyle = BarStyle.SEGMENTED_12;
    private String cachedBossBarTemplate = null;
    private ZoneId cachedZoneId = ZoneId.of("Europe/Istanbul");

    public AutoRestartSystem(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        // Check if the auto-restart module is enabled in configuration
        boolean isModuleEnabled = plugin.getConfig().getBoolean("modules.auto-restart",
                plugin.getConfig().getBoolean("modules.otorestart", true));
        boolean isSectionEnabled = plugin.getConfig().getBoolean("auto-restart.enabled", true);

        if (!isModuleEnabled || !isSectionEnabled) {
            plugin.logDebug("[AutoRestart] Module is disabled in config.");
            return;
        }

        this.enabled = true;
        this.cachedZoneId = plugin.getValidZoneId();
        this.restartTimes = loadRestartTimes();
        this.leadTimeSeconds = parseDurationToSeconds(plugin.getConfig().getString("auto-restart.bossbar.lead-time", "30m"), 1800L);
        this.restartCommand = plugin.getConfig().getString("auto-restart.restart-command", "restart");

        String colorStr = plugin.getConfig().getString("auto-restart.bossbar.color", "YELLOW");
        try {
            this.cachedBarColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (Exception e) {
            this.cachedBarColor = BarColor.YELLOW;
        }

        String styleStr = plugin.getConfig().getString("auto-restart.bossbar.style",
                plugin.getConfig().getString("auto-restart.bossbar.type", "SEGMENTED_12"));
        try {
            this.cachedBarStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (Exception e) {
            this.cachedBarStyle = BarStyle.SEGMENTED_12;
        }

        this.cachedBossBarTemplate = plugin.getLanguageManager().getString("auto-restart.bossbar-text",
                "&#FFAA00&lRESTART &8» &fServer will restart in &#FFAA00%remaining_time%&f!");

        if (restartTimes.isEmpty()) {
            plugin.getLogger().warning("[AutoRestart] No valid restart times configured in auto-restart.times!");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Run initial check immediately so BossBar appears right upon reload
        checkTimer();

        // Check every second (20 ticks)
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimer, 20L, 20L);

        ZoneId zoneId = plugin.getValidZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = getNextRestartTime(now);
        String nextStr = next != null ? next.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A";
        long rem = next != null ? Duration.between(now, next).getSeconds() : -1;

        plugin.getLogger().info("[AutoRestart] System initialized. Scheduled times: " + getFormattedRestartTimes() +
                " | Current Time: " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " (" + zoneId.getId() + ")" +
                " | Next Restart: " + nextStr + (rem >= 0 ? " (in " + formatTime(rem) + ")" : "") +
                " | BossBar Lead Time: " + leadTimeSeconds + "s");
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (restartBossBar != null) {
            restartBossBar.removeAll();
            restartBossBar = null;
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        this.enabled = false;
    }

    private void checkTimer() {
        if (!enabled || restartTimes.isEmpty()) return;

        ZoneId zoneId = cachedZoneId != null ? cachedZoneId : plugin.getValidZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        ZonedDateTime nextRestart = getNextRestartTime(now);
        if (nextRestart == null) return;

        long remainingSeconds = Duration.between(now, nextRestart).getSeconds();

        // 1. Time to restart
        if (remainingSeconds <= 0) {
            executeRestart();
            return;
        }

        // 2. Within BossBar display window (e.g., remaining time <= leadTimeSeconds)
        if (remainingSeconds <= leadTimeSeconds) {
            updateBossBar(remainingSeconds);
        } else {
            // If before lead time window and BossBar is visible, remove it
            if (restartBossBar != null) {
                restartBossBar.removeAll();
                restartBossBar = null;
            }
        }
    }

    private void updateBossBar(long remainingSeconds) {
        String formattedTime = formatTime(remainingSeconds);
        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        long seconds = remainingSeconds % 60;

        String rawBossBarText = cachedBossBarTemplate != null ? cachedBossBarTemplate :
                plugin.getLanguageManager().getString("auto-restart.bossbar-text",
                "&#FFAA00&lRESTART &8» &fServer will restart in &#FFAA00%remaining_time%&f!");

        String bossBarTitle = plugin.translateHexColorCodes(
                rawBossBarText.replace("%remaining_time%", formattedTime)
                              .replace("%time%", formattedTime)
                              .replace("%sure%", formattedTime)
                              .replace("%süre%", formattedTime)
                              .replace("%hours%", String.valueOf(hours))
                              .replace("%minutes%", String.valueOf(minutes))
                              .replace("%seconds%", String.valueOf(seconds))
        );

        if (restartBossBar == null) {
            restartBossBar = Bukkit.createBossBar(bossBarTitle, cachedBarColor, cachedBarStyle);
            restartBossBar.setProgress(1.0);
            restartBossBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                restartBossBar.addPlayer(p);
            }
        } else {
            restartBossBar.setTitle(bossBarTitle);
        }

        // Calculate progress (1.0 -> 0.0)
        double progress = (double) remainingSeconds / (double) leadTimeSeconds;
        restartBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void executeRestart() {
        stop();

        plugin.getLogger().info("[AutoRestart] Executing scheduled server restart now...");

        // Save worlds and player data
        try {
            Bukkit.savePlayers();
            for (World world : Bukkit.getWorlds()) {
                world.save();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AutoRestart] Error saving worlds: " + e.getMessage());
        }

        // Restart or stop server
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (restartCommand != null && !restartCommand.trim().isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), restartCommand);
                } else {
                    Bukkit.spigot().restart();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[AutoRestart] Restart command fallback to Bukkit.shutdown(): " + t.getMessage());
                Bukkit.shutdown();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (restartBossBar != null) {
            restartBossBar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (restartBossBar != null) {
            restartBossBar.removePlayer(event.getPlayer());
        }
    }

    private ZonedDateTime getNextRestartTime(ZonedDateTime now) {
        if (restartTimes.isEmpty()) return null;

        ZonedDateTime earliestUpcoming = null;

        for (LocalTime time : restartTimes) {
            ZonedDateTime candidate = now.with(time);
            // If time has passed today, move to tomorrow
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }

            if (earliestUpcoming == null || candidate.isBefore(earliestUpcoming)) {
                earliestUpcoming = candidate;
            }
        }

        return earliestUpcoming;
    }

    private List<LocalTime> loadRestartTimes() {
        List<LocalTime> list = new ArrayList<>();

        List<?> rawObjects = plugin.getConfig().getList("auto-restart.times");
        if (rawObjects == null || rawObjects.isEmpty()) {
            rawObjects = plugin.getConfig().getList("auto-restart.time");
        }

        List<String> rawStrings = new ArrayList<>();
        if (rawObjects != null && !rawObjects.isEmpty()) {
            for (Object obj : rawObjects) {
                if (obj != null) rawStrings.add(String.valueOf(obj));
            }
        } else {
            String single = plugin.getConfig().getString("auto-restart.times");
            if (single == null || single.trim().isEmpty()) {
                single = plugin.getConfig().getString("auto-restart.time");
            }
            if (single != null && !single.trim().isEmpty()) {
                rawStrings.add(single.trim());
            }
        }

        if (rawStrings.isEmpty()) {
            // Default times
            rawStrings = List.of("04:00", "16:00");
        }

        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("H:m:s"),
                DateTimeFormatter.ofPattern("HH:mm:ss"),
                DateTimeFormatter.ofPattern("H:m"),
                DateTimeFormatter.ofPattern("HH:mm")
        };

        for (String raw : rawStrings) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            LocalTime parsed = parseFlexibleTime(trimmed, formatters);
            if (parsed != null) {
                list.add(parsed);
            } else {
                plugin.getLogger().warning("[AutoRestart] Invalid time format in config: '" + raw + "' (Expected format: HH:mm e.g. 04:00 or 16:30)");
            }
        }

        return list;
    }

    private LocalTime parseFlexibleTime(String raw, DateTimeFormatter[] formatters) {
        String s = raw.trim();
        // Replace dot notation (e.g. 19.55 -> 19:55)
        if (s.contains(".") && !s.contains(":")) {
            s = s.replace('.', ':');
        }

        // Try standard formatters first
        for (DateTimeFormatter dtf : formatters) {
            try {
                return LocalTime.parse(s, dtf);
            } catch (Exception ignored) {}
        }

        // Handle sexagesimal integers produced by YAML 1.1 parser (e.g. 19:55 -> 1195)
        if (s.matches("^\\d+$")) {
            try {
                int val = Integer.parseInt(s);
                if (val >= 0 && val < 1440) {
                    return LocalTime.of(val / 60, val % 60);
                } else if (val >= 1440 && val < 86400) {
                    return LocalTime.of(val / 3600, (val % 3600) / 60, val % 60);
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private long parseDurationToSeconds(String input, long defaultValue) {
        if (input == null || input.trim().isEmpty()) return defaultValue;
        String s = input.trim().toLowerCase();
        try {
            if (s.endsWith("h") || s.endsWith("saat")) {
                long val = Long.parseLong(s.replaceAll("[^0-9]", ""));
                return val * 3600L;
            } else if (s.endsWith("m") || s.endsWith("dk") || s.endsWith("dakika") || s.endsWith("min")) {
                long val = Long.parseLong(s.replaceAll("[^0-9]", ""));
                return val * 60L;
            } else if (s.endsWith("s") || s.endsWith("sn") || s.endsWith("sec")) {
                return Long.parseLong(s.replaceAll("[^0-9]", ""));
            } else {
                return Long.parseLong(s);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AutoRestart] Could not parse lead-time: '" + input + "'. Using default: " + defaultValue + "s");
            return defaultValue;
        }
    }

    private String formatTime(long totalSecs) {
        if (plugin.getLanguageManager() != null) {
            return plugin.getLanguageManager().formatTime(totalSecs);
        }
        if (totalSecs <= 0) return "0s";
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private String getFormattedRestartTimes() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < restartTimes.size(); i++) {
            sb.append(restartTimes.get(i).format(DateTimeFormatter.ofPattern("HH:mm")));
            if (i < restartTimes.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public BossBar getRestartBossBar() {
        return restartBossBar;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

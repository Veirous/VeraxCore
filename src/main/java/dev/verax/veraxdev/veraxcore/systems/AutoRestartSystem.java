package dev.verax.veraxdev.veraxcore.systems;

import dev.verax.veraxdev.veraxcore.VeraxCore;
import dev.verax.veraxdev.veraxcore.utils.AdventureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private boolean isListenerRegistered = false;
    private List<LocalTime> restartTimes = new ArrayList<>();
    private long leadTimeSeconds = 1800L; // Default 30 minutes (1800 seconds)
    private String restartCommand = "restart";
    private BarColor cachedBarColor = BarColor.YELLOW;
    private BarStyle cachedBarStyle = BarStyle.SEGMENTED_12;
    private String cachedBossBarTemplate = null;
    private ZoneId cachedZoneId = ZoneId.of("Europe/Istanbul");
    private boolean bossbarEnabled = true;
    private boolean broadcastEnabled = true;
    private List<Long> broadcastIntervals = new ArrayList<>();
    private boolean titleEnabled = true;
    private int titleCountdownSeconds = 5;
    private boolean soundEnabled = true;
    private String soundName = "BLOCK_NOTE_BLOCK_PLING";

    // Manual countdown fields (/restarttimer <time>)
    private boolean manualCountdownActive = false;
    private long manualTotalSeconds = 0;
    private long manualRemainingSeconds = 0;
    private String manualStarter = "Console";
    private long lastNotifiedSecond = -1;

    public AutoRestartSystem(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        // Check if the auto-restart module is enabled in configuration
        boolean isModuleEnabled = plugin.getConfig().getBoolean("modules.auto-restart",
                plugin.getConfig().getBoolean("modules.otorestart", true));
        boolean isSectionEnabled = plugin.getConfig().getBoolean("auto-restart.enabled", true);

        loadConfigurationValues();

        if (!isModuleEnabled || !isSectionEnabled) {
            plugin.logDebug("[AutoRestart] Scheduled restart module is disabled in config.");
            return;
        }

        this.enabled = true;
        this.lastNotifiedSecond = -1;
        this.restartTimes = loadRestartTimes();

        if (restartTimes.isEmpty()) {
            plugin.getLogger().warning("[AutoRestart] No valid restart times configured in auto-restart.times!");
            return;
        }

        ensureListenerRegistered();

        // Run initial check immediately so BossBar appears right upon reload
        checkTimer();

        // Check every second (20 ticks)
        ensureTimerTaskRunning();

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

    private void loadConfigurationValues() {
        this.cachedZoneId = plugin.getValidZoneId();
        this.bossbarEnabled = plugin.getConfig().getBoolean("auto-restart.bossbar.enabled", true);
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

        this.broadcastEnabled = plugin.getConfig().getBoolean("auto-restart.broadcast.enabled", true);
        List<String> rawIntervals = plugin.getConfig().getStringList("auto-restart.broadcast.intervals");
        this.broadcastIntervals = new ArrayList<>();
        if (rawIntervals.isEmpty()) {
            this.broadcastIntervals.addAll(List.of(1800L, 900L, 600L, 300L, 180L, 120L, 60L, 30L, 15L, 10L));
        } else {
            for (String raw : rawIntervals) {
                long secs = parseDurationToSeconds(raw, -1L);
                if (secs > 0 && !this.broadcastIntervals.contains(secs)) {
                    this.broadcastIntervals.add(secs);
                }
            }
            Collections.sort(this.broadcastIntervals, Collections.reverseOrder());
        }

        this.titleEnabled = plugin.getConfig().getBoolean("auto-restart.title.enabled", true);
        this.titleCountdownSeconds = Math.max(1, plugin.getConfig().getInt("auto-restart.title.countdown-seconds", 5));

        this.soundEnabled = plugin.getConfig().getBoolean("auto-restart.sound.enabled", true);
        this.soundName = plugin.getConfig().getString("auto-restart.sound.name", "BLOCK_NOTE_BLOCK_PLING");
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
        unregisterListener();
        this.enabled = false;
        this.manualCountdownActive = false;
        this.manualTotalSeconds = 0;
        this.manualRemainingSeconds = 0;
    }

    private void ensureTimerTaskRunning() {
        if (timerTask == null || timerTask.isCancelled()) {
            timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimer, 20L, 20L);
        }
    }

    private void ensureListenerRegistered() {
        if (!isListenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            isListenerRegistered = true;
        }
    }

    private void unregisterListener() {
        if (isListenerRegistered) {
            org.bukkit.event.HandlerList.unregisterAll(this);
            isListenerRegistered = false;
        }
    }

    private void checkTimer() {
        // 1. Manual countdown triggered by /restarttimer
        if (manualCountdownActive) {
            if (manualRemainingSeconds <= 0) {
                manualCountdownActive = false;
                executeRestart();
                return;
            }

            updateManualBossBar(manualRemainingSeconds, manualTotalSeconds);
            handleMilestones(manualRemainingSeconds);
            manualRemainingSeconds--;
            return;
        }

        // 2. Scheduled automatic restart check
        if (!enabled || restartTimes.isEmpty()) return;

        ZoneId zoneId = cachedZoneId != null ? cachedZoneId : plugin.getValidZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        ZonedDateTime nextRestart = getNextRestartTime(now);
        if (nextRestart == null) return;

        long remainingSeconds = Duration.between(now, nextRestart).getSeconds();

        // Time to restart
        if (remainingSeconds <= 0) {
            executeRestart();
            return;
        }

        // BossBar display window
        if (bossbarEnabled && remainingSeconds <= leadTimeSeconds) {
            updateBossBar(remainingSeconds);
        } else {
            // Before lead time window or if BossBar is disabled: remove bossbar if visible
            if (restartBossBar != null) {
                restartBossBar.removeAll();
                restartBossBar = null;
            }
        }
        handleMilestones(remainingSeconds);
    }

    public boolean startManualRestart(long seconds, String starterName) {
        if (seconds <= 0) return false;

        loadConfigurationValues();

        this.manualCountdownActive = true;
        this.manualTotalSeconds = seconds;
        this.manualRemainingSeconds = seconds;
        this.manualStarter = (starterName != null && !starterName.trim().isEmpty()) ? starterName : "Console";
        this.lastNotifiedSecond = -1;

        ensureListenerRegistered();
        ensureTimerTaskRunning();

        updateManualBossBar(manualRemainingSeconds, manualTotalSeconds);

        String formattedTime = formatTime(seconds);
        String msg = plugin.getLanguageManager().getString("auto-restart.manual-started",
                "&#FFAA00[Restart] &fServer restart timer initiated! Remaining time: &#FFAA00%time%")
                .replace("%time%", formattedTime)
                .replace("%remaining_time%", formattedTime)
                .replace("%starter%", manualStarter);
        Bukkit.broadcastMessage(plugin.translateHexColorCodes(msg));

        playSoundToAll(this.soundName, 1.0f, 1.0f);
        return true;
    }

    public boolean cancelManualRestart(CommandSender sender) {
        if (!manualCountdownActive) return false;

        manualCountdownActive = false;
        manualTotalSeconds = 0;
        manualRemainingSeconds = 0;
        this.lastNotifiedSecond = -1;

        if (restartBossBar != null) {
            restartBossBar.removeAll();
            restartBossBar = null;
        }

        String canceller = sender != null ? sender.getName() : "Console";
        String msg = plugin.getLanguageManager().getString("auto-restart.manual-cancelled",
                "&#55FF55[Restart] &fServer restart cancelled by &#FFAA00%canceller%&f.")
                .replace("%canceller%", canceller);
        Bukkit.broadcastMessage(plugin.translateHexColorCodes(msg));

        // If scheduled auto restart is disabled or not running, cancel task
        boolean isModuleEnabled = plugin.getConfig().getBoolean("modules.auto-restart",
                plugin.getConfig().getBoolean("modules.otorestart", true));
        boolean isSectionEnabled = plugin.getConfig().getBoolean("auto-restart.enabled", true);
        if (!isModuleEnabled || !isSectionEnabled || restartTimes.isEmpty()) {
            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            unregisterListener();
        }

        return true;
    }

    private void updateManualBossBar(long remainingSeconds, long totalSeconds) {
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
                              .replace("%hours%", String.valueOf(hours))
                              .replace("%minutes%", String.valueOf(minutes))
                              .replace("%seconds%", String.valueOf(seconds))
        );

        BarColor color = (remainingSeconds <= 10) ? BarColor.RED : (cachedBarColor != null ? cachedBarColor : BarColor.YELLOW);

        if (restartBossBar == null) {
            restartBossBar = Bukkit.createBossBar(bossBarTitle, color, cachedBarStyle != null ? cachedBarStyle : BarStyle.SEGMENTED_12);
            restartBossBar.setProgress(1.0);
            restartBossBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                restartBossBar.addPlayer(p);
            }
        } else {
            restartBossBar.setTitle(bossBarTitle);
            restartBossBar.setColor(color);
        }

        double progress = (double) remainingSeconds / (double) Math.max(1L, totalSeconds);
        restartBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void handleMilestones(long remainingSeconds) {
        if (remainingSeconds == lastNotifiedSecond) return;

        boolean isBroadcastMark = broadcastEnabled && broadcastIntervals.contains(remainingSeconds);
        boolean isTitleMark = titleEnabled && (remainingSeconds <= titleCountdownSeconds && remainingSeconds >= 1);

        if (!isBroadcastMark && !isTitleMark) return;

        lastNotifiedSecond = remainingSeconds;
        String formattedTime = formatTime(remainingSeconds);

        // Chat notification for configured intervals
        if (isBroadcastMark) {
            String msg = plugin.getLanguageManager().getString("auto-restart.broadcast",
                    plugin.getLanguageManager().getString("auto-restart.manual-broadcast",
                    "&#FFAA00&lRESTART &8» &fServer will restart in &#FFAA00%remaining_time%!"))
                    .replace("%remaining_time%", formattedTime)
                    .replace("%time%", formattedTime);
            Bukkit.broadcastMessage(plugin.translateHexColorCodes(msg));
        }

        // Title and alert sound for the countdown seconds
        if (isTitleMark) {
            if (soundEnabled) {
                float pitch = 1.0f + (0.15f * Math.max(0, titleCountdownSeconds - remainingSeconds));
                playSoundToAll(soundName, 1.0f, pitch);
            }

            String titleText = plugin.getLanguageManager().getString("auto-restart.title",
                    plugin.getLanguageManager().getString("auto-restart.manual-title", "&#FF5555&lSERVER RESTART"));
            String subtitleText = plugin.getLanguageManager().getString("auto-restart.subtitle",
                    plugin.getLanguageManager().getString("auto-restart.manual-subtitle", "&fRestarting in &#FFAA00%seconds% &fseconds!"))
                    .replace("%seconds%", String.valueOf(remainingSeconds))
                    .replace("%remaining_time%", formattedTime)
                    .replace("%time%", formattedTime);

            for (Player p : Bukkit.getOnlinePlayers()) {
                AdventureUtil.sendTitle(p, titleText, subtitleText, 0, 25, 5);
            }
        } else if (soundEnabled && isBroadcastMark) {
            playSoundToAll(soundName, 0.8f, 1.0f);
        }
    }

    private void playSoundToAll(String soundName, float volume, float pitch) {
        if (!soundEnabled) return;
        try {
            Sound sound;
            try {
                sound = Sound.valueOf(soundName != null ? soundName.toUpperCase() : this.soundName.toUpperCase());
            } catch (Exception e) {
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        } catch (Throwable ignored) {}
    }

    private void updateBossBar(long remainingSeconds) {
        if (!bossbarEnabled) {
            if (restartBossBar != null) {
                restartBossBar.removeAll();
                restartBossBar = null;
            }
            return;
        }
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
                              .replace("%hours%", String.valueOf(hours))
                              .replace("%minutes%", String.valueOf(minutes))
                              .replace("%seconds%", String.valueOf(seconds))
        );

        BarColor color = (remainingSeconds <= 10) ? BarColor.RED : (cachedBarColor != null ? cachedBarColor : BarColor.YELLOW);

        if (restartBossBar == null) {
            restartBossBar = Bukkit.createBossBar(bossBarTitle, color, cachedBarStyle != null ? cachedBarStyle : BarStyle.SEGMENTED_12);
            restartBossBar.setProgress(1.0);
            restartBossBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                restartBossBar.addPlayer(p);
            }
        } else {
            restartBossBar.setTitle(bossBarTitle);
            restartBossBar.setColor(color);
        }

        // Calculate progress (1.0 -> 0.0)
        double progress = (double) remainingSeconds / (double) Math.max(1L, leadTimeSeconds);
        restartBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void executeRestart() {
        stop();

        plugin.getLogger().info("[AutoRestart] Executing server restart now...");

        // Save worlds and player data
        try {
            Bukkit.savePlayers();
            for (World world : Bukkit.getWorlds()) {
                world.save();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AutoRestart] Error saving worlds: " + e.getMessage());
        }

        // Safe player kick message
        String kickMsg = plugin.getLanguageManager().getString("auto-restart.kick-message",
                "&c&lSERVER RESTART\n\n&7The server is currently restarting.\n&7Please reconnect in 1-2 minutes.");
        String formattedKick = plugin.translateHexColorCodes(kickMsg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.kickPlayer(formattedKick);
            } catch (Throwable ignored) {}
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
    public void onPlayerQuit(PlayerQuitEvent event) {
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

    public long parseDurationToSeconds(String input, long defaultValue) {
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
            plugin.getLogger().warning("[AutoRestart] Could not parse duration: '" + input + "'. Using default: " + defaultValue + "s");
            return defaultValue;
        }
    }

    public String formatTime(long totalSecs) {
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

    public boolean isManualRestartActive() {
        return manualCountdownActive;
    }

    public long getManualRemainingSeconds() {
        return manualRemainingSeconds;
    }

    public long getNextScheduledRemainingSeconds() {
        ZoneId zoneId = cachedZoneId != null ? cachedZoneId : plugin.getValidZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = getNextRestartTime(now);
        return next != null ? Duration.between(now, next).getSeconds() : -1L;
    }

    public BossBar getRestartBossBar() {
        return restartBossBar;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

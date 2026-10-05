package com.verax.veraxcore.managers;

import com.verax.veraxcore.VeraxCore;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public class MaintenanceManager {

    private final VeraxCore plugin;
    private boolean maintenanceActive = false;
    private BukkitTask countdownTask = null;
    private BossBar countdownBossBar = null;
    private long countdownTotalSeconds = 0;
    private long countdownRemainingSeconds = 0;

    private BukkitTask activeBossBarTask = null;
    private BossBar activeBossBar = null;
    private long maintenanceStartTime = 0L;

    public MaintenanceManager(VeraxCore plugin) {
        this.plugin = plugin;
        this.maintenanceActive = plugin.getConfig().getBoolean("maintenance.enabled", false);
        if (this.maintenanceActive) {
            this.maintenanceStartTime = plugin.getConfig().getLong("maintenance.start-time", System.currentTimeMillis());
            startActiveMaintenanceBossBar();
        }
    }

    public boolean isMaintenanceActive() {
        return maintenanceActive;
    }

    public boolean isCountdownActive() {
        return countdownTask != null;
    }

    public long getCountdownRemainingSeconds() {
        return countdownRemainingSeconds;
    }

    public BossBar getActiveBossBar() {
        return activeBossBar;
    }

    public BossBar getCountdownBossBar() {
        return countdownBossBar;
    }

    public void setMaintenanceActive(boolean active) {
        if (countdownTask != null) {
            cancelCountdown();
        }

        this.maintenanceActive = active;
        plugin.getConfig().set("maintenance.enabled", active);

        if (active) {
            this.maintenanceStartTime = System.currentTimeMillis();
            plugin.getConfig().set("maintenance.start-time", this.maintenanceStartTime);
            plugin.saveConfigSafely();
            kickNonWhitelistedPlayers();
            startActiveMaintenanceBossBar();
        } else {
            this.maintenanceStartTime = 0L;
            plugin.getConfig().set("maintenance.start-time", 0L);
            plugin.saveConfigSafely();
            stopActiveMaintenanceBossBar();
        }
    }

    public void startActiveMaintenanceBossBar() {
        stopActiveMaintenanceBossBar();

        String colorStr = plugin.getConfig().getString("maintenance.active-bossbar.color",
                plugin.getConfig().getString("maintenance.bossbar.color", "RED"));
        BarColor barColor;
        try {
            barColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (Exception e) {
            barColor = BarColor.RED;
        }

        String styleStr = plugin.getConfig().getString("maintenance.active-bossbar.style",
                plugin.getConfig().getString("maintenance.bossbar.style", "SEGMENTED_12"));
        BarStyle barStyle;
        try {
            barStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (Exception e) {
            barStyle = BarStyle.SEGMENTED_12;
        }

        long elapsedSecs = Math.max(0, (System.currentTimeMillis() - maintenanceStartTime) / 1000L);
        String formattedTime = formatTime(elapsedSecs);
        String rawBossBarText = plugin.getLanguageManager().getString("maintenance.active-bossbar-text",
                "&#FF3333&lMAINTENANCE MODE &8» &fElapsed Time: &#FF3333%elapsed_time%");
        String bossBarTitle = plugin.translateHexColorCodes(
                rawBossBarText.replace("%elapsed_time%", formattedTime)
                              .replace("%time%", formattedTime)
                              .replace("%gecen_sure%", formattedTime)
                              .replace("%süre%", formattedTime)
                              .replace("%sure%", formattedTime)
        );

        activeBossBar = Bukkit.createBossBar(bossBarTitle, barColor, barStyle);
        activeBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            activeBossBar.addPlayer(p);
        }

        activeBossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!maintenanceActive) {
                    stopActiveMaintenanceBossBar();
                    cancel();
                    return;
                }

                long eSecs = Math.max(0, (System.currentTimeMillis() - maintenanceStartTime) / 1000L);
                String fTime = formatTime(eSecs);
                String updatedTitle = plugin.translateHexColorCodes(
                        rawBossBarText.replace("%elapsed_time%", fTime)
                                      .replace("%time%", fTime)
                                      .replace("%gecen_sure%", fTime)
                                      .replace("%süre%", fTime)
                                      .replace("%sure%", fTime)
                );
                activeBossBar.setTitle(updatedTitle);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!activeBossBar.getPlayers().contains(p)) {
                        activeBossBar.addPlayer(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopActiveMaintenanceBossBar() {
        if (activeBossBarTask != null) {
            activeBossBarTask.cancel();
            activeBossBarTask = null;
        }
        if (activeBossBar != null) {
            activeBossBar.removeAll();
            activeBossBar = null;
        }
    }

    public void kickNonWhitelistedPlayers() {
        String kickMsg = getKickMessage();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!canBypassMaintenance(p)) {
                p.kickPlayer(kickMsg);
            }
        }
    }

    public boolean canBypassMaintenance(Player player) {
        if (player == null) return false;
        if (player.isOp()) return true;
        if (player.hasPermission("veraxcore.admin")) return true;
        if (player.hasPermission("veraxcore.maintenance.bypass") || player.hasPermission("veraxcore.bakim.bypass")) return true;

        List<String> allowedRanks = plugin.getConfig().getStringList("maintenance.allowed-ranks");
        if (allowedRanks.isEmpty()) {
            allowedRanks = plugin.getConfig().getStringList("allowed-ranks");
        }

        for (String rank : allowedRanks) {
            if (player.hasPermission("group." + rank.toLowerCase())) {
                return true;
            }
        }

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String primaryGroup = user.getPrimaryGroup().toLowerCase();
                for (String rank : allowedRanks) {
                    if (primaryGroup.equalsIgnoreCase(rank.trim())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public boolean canBypassMaintenance(UUID uuid) {
        if (uuid == null) return false;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return canBypassMaintenance(online);
        }

        List<String> allowedRanks = plugin.getConfig().getStringList("maintenance.allowed-ranks");
        if (allowedRanks.isEmpty()) {
            allowedRanks = plugin.getConfig().getStringList("allowed-ranks");
        }

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user != null) {
                String primaryGroup = user.getPrimaryGroup().toLowerCase();
                for (String rank : allowedRanks) {
                    if (primaryGroup.equalsIgnoreCase(rank.trim())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public boolean startCountdown(long totalSeconds, CommandSender sender) {
        if (totalSeconds <= 0) return false;

        if (countdownTask != null) {
            cancelCountdown();
        }

        this.countdownTotalSeconds = totalSeconds;
        this.countdownRemainingSeconds = totalSeconds;

        String colorStr = plugin.getConfig().getString("maintenance.bossbar.color", "RED");
        BarColor barColor;
        try {
            barColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (Exception e) {
            barColor = BarColor.RED;
        }

        String styleStr = plugin.getConfig().getString("maintenance.bossbar.style", "SEGMENTED_12");
        BarStyle barStyle;
        try {
            barStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (Exception e) {
            barStyle = BarStyle.SEGMENTED_12;
        }

        String formattedTime = formatTime(countdownRemainingSeconds);
        String rawBossBarText = plugin.getLanguageManager().getString("maintenance.bossbar-text",
                "&#FF3333&lMAINTENANCE MODE &8» &fServer will enter maintenance in &#FF3333%remaining_time%&f!");
        String bossBarTitle = plugin.translateHexColorCodes(
                rawBossBarText.replace("%remaining_time%", formattedTime)
                              .replace("%time%", formattedTime)
                              .replace("%süre%", formattedTime)
                              .replace("%sure%", formattedTime)
        );

        countdownBossBar = Bukkit.createBossBar(bossBarTitle, barColor, barStyle);
        countdownBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            countdownBossBar.addPlayer(p);
        }

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownRemainingSeconds <= 0) {
                    cancelCountdown();
                    setMaintenanceActive(true);
                    String broadcastKey = plugin.getLanguageManager().contains("maintenance.broadcast-started") ?
                            "maintenance.broadcast-started" : "messages.maintenance-started";
                    String broadcastMsg = plugin.getMessage(broadcastKey);
                    if (broadcastMsg != null && !broadcastMsg.trim().isEmpty()) {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.sendMessage(broadcastMsg);
                        }
                    }
                    return;
                }

                countdownRemainingSeconds--;

                double progress = (double) countdownRemainingSeconds / (double) countdownTotalSeconds;
                countdownBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                String fTime = formatTime(countdownRemainingSeconds);
                String updatedTitle = plugin.translateHexColorCodes(
                        rawBossBarText.replace("%remaining_time%", fTime)
                                      .replace("%time%", fTime)
                                      .replace("%süre%", fTime)
                                      .replace("%sure%", fTime)
                );
                countdownBossBar.setTitle(updatedTitle);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!countdownBossBar.getPlayers().contains(p)) {
                        countdownBossBar.addPlayer(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return true;
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (countdownBossBar != null) {
            countdownBossBar.removeAll();
            countdownBossBar = null;
        }
        countdownTotalSeconds = 0;
        countdownRemainingSeconds = 0;
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
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    public String getKickMessage() {
        String raw = plugin.getLanguageManager().getString("maintenance.kick-screen",
                "&c&lSERVER UNDER MAINTENANCE\n\n&7Our server is currently under maintenance.\n&7Please check back later.\n\n&eDiscord: &fdiscord.gg/verax");
        return plugin.translateHexColorCodes(raw);
    }

    public String getMotdLine1() {
        String raw = plugin.getLanguageManager().getString("maintenance.motd-line-1",
                "&#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &#FF3333&lMAINTENANCE MODE");
        return plugin.translateHexColorCodes(raw);
    }

    public String getMotdLine2() {
        String raw = plugin.getLanguageManager().getString("maintenance.motd-line-2",
                "&7Our server is currently under maintenance. &8(&c/discord&8)");
        return plugin.translateHexColorCodes(raw);
    }

    public void onDisable() {
        cancelCountdown();
        stopActiveMaintenanceBossBar();
    }
}

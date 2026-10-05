package com.verax.veraxcore.systems;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AutoAnnouncementSystem {

    private final VeraxCore plugin;
    private final List<BukkitTask> activeTasks = new ArrayList<>();

    public AutoAnnouncementSystem(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop(); // Cancel previously running tasks

        if (!plugin.getConfig().getBoolean("modules.auto-announcement", plugin.getConfig().getBoolean("modules.otoduyuru", true))) {
            return;
        }

        ConfigurationSection announcementsSec = plugin.getConfig().getConfigurationSection("auto-announcement.announcements");
        if (announcementsSec == null) {
            announcementsSec = plugin.getConfig().getConfigurationSection("oto-duyuru.duyurular");
        }
        if (announcementsSec == null) {
            return;
        }

        String prefix = plugin.getConfig().getString("prefix", "");

        for (String key : announcementsSec.getKeys(false)) {
            ConfigurationSection sec = announcementsSec.getConfigurationSection(key);
            if (sec == null) continue;

            boolean enabled = sec.getBoolean("enabled", true);
            if (!enabled) continue;

            long intervalSeconds = sec.getLong("interval-seconds", 180L);
            if (intervalSeconds <= 0) intervalSeconds = 180L;
            long intervalTicks = intervalSeconds * 20L;

            String messageText = sec.getString("message", sec.getString("mesaj", ""));
            if (messageText == null || messageText.trim().isEmpty()) continue;

            boolean soundEnabled = sec.getBoolean("sound-enabled", true);
            String soundName = sec.getString("sound", "ENTITY_PLAYER_LEVELUP");

            // Schedule an independent timer for each announcement
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                sendAnnouncement(messageText, prefix, soundEnabled, soundName);
            }, intervalTicks, intervalTicks);

            activeTasks.add(task);
            plugin.logDebug("[AutoAnnouncement] '" + key + "' announcement scheduled to run every " + intervalSeconds + " seconds.");
        }
    }

    public void stop() {
        for (BukkitTask task : activeTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }

    public void reload() {
        stop();
        start();
    }


    private void sendAnnouncement(String messageText, String prefix, boolean soundEnabled, String soundName) {
        String processedMessage = messageText.replace("<prefix>", prefix).replace("%prefix%", prefix);
        String coloredMessage = plugin.translateHexColorCodes(processedMessage);

        Sound sound = null;
        if (soundEnabled && soundName != null && !soundName.isEmpty()) {
            try {
                sound = Sound.valueOf(soundName.toUpperCase());
            } catch (Exception ignored) {}
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getPlayerSettingsManager().isAutoAnnouncementEnabled(player.getUniqueId())) {
                continue;
            }
            player.sendMessage(coloredMessage);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }
}

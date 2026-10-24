package com.verax.veraxcore.managers;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingsManager {

    private final VeraxCore plugin;
    private final Map<UUID, Boolean> tradeSettings = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> autoAnnouncementSettings = new ConcurrentHashMap<>();

    public PlayerSettingsManager(VeraxCore plugin) {
        this.plugin = plugin;
        loadOnlinePlayers();
    }

    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerSettingsAsync(player.getUniqueId());
        }
    }

    public void loadPlayerSettingsAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean[] settings = plugin.getDatabaseManager().loadPlayerSettings(uuid);
            tradeSettings.put(uuid, settings[0]);
            autoAnnouncementSettings.put(uuid, settings[1]);
        });
    }

    public void unloadPlayerSettings(UUID uuid) {
        tradeSettings.remove(uuid);
        autoAnnouncementSettings.remove(uuid);
    }

    public boolean isTradeEnabled(UUID uuid) {
        return tradeSettings.getOrDefault(uuid, true);
    }

    public void setTradeEnabled(UUID uuid, boolean enabled) {
        tradeSettings.put(uuid, enabled);
        savePlayerSettingsAsync(uuid);
    }

    public boolean isAutoAnnouncementEnabled(UUID uuid) {
        return autoAnnouncementSettings.getOrDefault(uuid, true);
    }

    public void setAutoAnnouncementEnabled(UUID uuid, boolean enabled) {
        autoAnnouncementSettings.put(uuid, enabled);
        savePlayerSettingsAsync(uuid);
    }

    private void savePlayerSettingsAsync(UUID uuid) {
        boolean trade = isTradeEnabled(uuid);
        boolean announcement = isAutoAnnouncementEnabled(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerSettings(uuid, trade, announcement);
        });
    }
}

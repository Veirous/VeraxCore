package com.verax.veraxcore.listeners;

import com.verax.veraxcore.VeraxCore;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class EssentialsAfkListener implements Listener {

    private final VeraxCore plugin;

    public EssentialsAfkListener(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAfkStatusChange(AfkStatusChangeEvent event) {
        Player player = event.getAffected().getBase();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        if (!ActivityListener.onlineStaffCache.contains(uuid)) return;

        boolean isAfk = event.getValue();
        if (isAfk) {
            // Player went AFK: Stop timer and save playtime accumulated so far
            if (ActivityListener.loginTimes.containsKey(uuid)) {
                long joinTime = ActivityListener.loginTimes.remove(uuid);
                int sessionSeconds = (int) ((System.currentTimeMillis() - joinTime) / 1000);
                if (sessionSeconds > 0) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDatabaseManager().addSeconds(uuid, "daily", sessionSeconds);
                        plugin.getDatabaseManager().addSeconds(uuid, "weekly", sessionSeconds);
                        plugin.getDatabaseManager().addSeconds(uuid, "monthly", sessionSeconds);
                        plugin.getDatabaseManager().addSeconds(uuid, "total", sessionSeconds);
                    });
                }
            }
        } else {
            // Player returned from AFK: Restart session timer from current timestamp
            ActivityListener.loginTimes.put(uuid, System.currentTimeMillis());
        }
    }
}

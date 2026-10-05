package com.verax.veraxcore.listeners;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.managers.MaintenanceManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

public class MaintenanceListener implements Listener {

    private final VeraxCore plugin;
    private final MaintenanceManager maintenanceManager;

    public MaintenanceListener(VeraxCore plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("modules.maintenance", plugin.getConfig().getBoolean("modules.bakim", true))) return;
        if (!plugin.getConfig().getBoolean("maintenance.change-motd", false)) return;

        if (maintenanceManager.isMaintenanceActive()) {
            String line1 = maintenanceManager.getMotdLine1();
            String line2 = maintenanceManager.getMotdLine2();
            if (line1 != null && line2 != null) {
                event.setMotd(line1 + "\n" + line2);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!plugin.getConfig().getBoolean("modules.maintenance", plugin.getConfig().getBoolean("modules.bakim", true))) return;

        if (maintenanceManager.isMaintenanceActive()) {
            if (!maintenanceManager.canBypassMaintenance(event.getPlayer())) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, maintenanceManager.getKickMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (maintenanceManager.isMaintenanceActive()) {
            if (maintenanceManager.getActiveBossBar() != null) {
                maintenanceManager.getActiveBossBar().addPlayer(event.getPlayer());
            }
        } else if (maintenanceManager.isCountdownActive()) {
            if (maintenanceManager.getCountdownBossBar() != null) {
                maintenanceManager.getCountdownBossBar().addPlayer(event.getPlayer());
            }
        }
    }
}

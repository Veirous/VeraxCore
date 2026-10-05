package com.verax.veraxcore.listeners;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.verax.veraxcore.commands.SpawnCommand;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpawnListener implements Listener {

    private final VeraxCore plugin;
    private final Set<UUID> firstJoinPlayers = new HashSet<>();

    public SpawnListener(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!event.getPlayer().hasPlayedBefore()) {
            firstJoinPlayers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("modules.spawn", true)) return;

        Player player = event.getPlayer();
        boolean firstJoin = firstJoinPlayers.remove(player.getUniqueId()) || !player.hasPlayedBefore();

        Location targetLocation;

        if (firstJoin) {
            // First join: check first-spawn-location first, fallback to spawn-location
            targetLocation = plugin.getLocationFromConfig("first-spawn-location");
            if (targetLocation == null) {
                targetLocation = plugin.getLocationFromConfig("spawn-location");
            }
        } else {
            // Normal join: check spawn-location first, fallback to first-spawn-location
            targetLocation = plugin.getLocationFromConfig("spawn-location");
            if (targetLocation == null) {
                targetLocation = plugin.getLocationFromConfig("first-spawn-location");
            }
        }

        if (targetLocation != null) {
            final Location finalLoc = targetLocation;
            // 5 tick delay: Ensure server join packets complete before teleporting
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.safeTeleport(player, finalLoc);
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("modules.spawn", true)) return;

        // Priority to First-Spawn
        Location firstSpawn = plugin.getLocationFromConfig("first-spawn-location");
        if (firstSpawn != null) {
            event.setRespawnLocation(firstSpawn);
            return;
        }

        // If no first spawn, teleport to normal spawn location
        Location spawn = plugin.getLocationFromConfig("spawn-location");
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    /**
     * Cancel teleport if movement on X, Y, or Z axes is detected (jump, walk, fall).
     * Only position change cancels; looking around (yaw/pitch) does not cancel.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!SpawnCommand.isTeleporting(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Use a small threshold to avoid floating-point imprecision issues.
        double threshold = 1e-6;
        boolean xChanged = Math.abs(from.getX() - to.getX()) > threshold;
        boolean yChanged = Math.abs(from.getY() - to.getY()) > threshold;
        boolean zChanged = Math.abs(from.getZ() - to.getZ()) > threshold;

        if (xChanged || yChanged || zChanged) {
            SpawnCommand.cancelTeleport(player, plugin);
        }
    }

    /**
     * Cancel teleport when the player takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (SpawnCommand.isTeleporting(player.getUniqueId())) {
            SpawnCommand.cancelTeleport(player, plugin);
        }
    }

    /**
     * Prevents other plugins from blocking spawn teleports.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (plugin.isUnblockableTeleport(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SpawnCommand.cancelTeleportQuietly(event.getPlayer().getUniqueId());
    }
}

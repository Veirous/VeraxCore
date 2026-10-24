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
    private final Set<UUID> firstJoinPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SpawnListener(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED && !event.getPlayer().hasPlayedBefore()) {
            firstJoinPlayers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("modules.spawn", true)) return;

        Player player = event.getPlayer();
        boolean firstJoin = firstJoinPlayers.remove(player.getUniqueId()) || !player.hasPlayedBefore();

        Location targetLocation = null;

        if (firstJoin) {
            boolean firstJoinSpawn = plugin.getConfig().getBoolean("first-join-spawn", plugin.getConfig().getBoolean("ilk-giris-spawn", true));
            if (!firstJoinSpawn) return;

            // First join: check first-spawn-location first, fallback to spawn-location
            targetLocation = plugin.getLocationFromConfig("first-spawn-location");
            if (targetLocation == null) {
                targetLocation = plugin.getLocationFromConfig("spawn-location");
            }
        } else {
            boolean spawnOnJoin = plugin.getConfig().getBoolean("spawn-on-join", plugin.getConfig().getBoolean("giriste-spawn", false));
            if (!spawnOnJoin) return;

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
     * Cancel teleport if physical movement away from start location is detected (walk, jump, fall, knockback).
     * Camera/mouse movement (yaw/pitch) does not cancel.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!SpawnCommand.hasActiveTeleports()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // 1. Fast check: If XYZ are identical, only yaw/pitch (head/camera) changed -> do not cancel!
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!SpawnCommand.isTeleporting(player.getUniqueId())) return;

        Location start = SpawnCommand.getStartLocation(player.getUniqueId());
        if (start == null || !start.getWorld().equals(to.getWorld())) {
            SpawnCommand.cancelTeleport(player, plugin);
            return;
        }

        // 2. Tolerance check: Allow tiny sub-pixel vibrations or block edge alignments.
        // Distance threshold: 0.5 blocks (0.5 squared = 0.25).
        // Only cancel if player walks, jumps, falls, or gets knocked back (> 0.5 blocks from start).
        double dx = to.getX() - start.getX();
        double dy = to.getY() - start.getY();
        double dz = to.getZ() - start.getZ();
        double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);

        if (distanceSquared > 0.25) {
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
        firstJoinPlayers.remove(event.getPlayer().getUniqueId());
        SpawnCommand.cancelTeleportQuietly(event.getPlayer().getUniqueId());
    }
}

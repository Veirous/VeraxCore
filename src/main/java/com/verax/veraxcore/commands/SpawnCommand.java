package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public SpawnCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    private static final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> activeTeleports = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isTeleporting(java.util.UUID uuid) {
        return activeTeleports.containsKey(uuid);
    }

    public static void cancelTeleport(Player player, VeraxCore plugin) {
        org.bukkit.scheduler.BukkitTask task = activeTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(plugin.getMessage("teleport-messages.teleport-cancelled"));
            playSound(player, plugin, "sounds.cancel");
            player.sendTitle("", "", 0, 1, 0);
        }
    }

    public static void cancelTeleportQuietly(java.util.UUID uuid) {
        org.bukkit.scheduler.BukkitTask task = activeTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Module check
        if (!plugin.getConfig().getBoolean("modules.spawn", true)) {
            // Fallback message if not found in configuration
            String disabledMsg = plugin.getMessage("messages.module-disabled");
            if (disabledMsg == null || disabledMsg.isEmpty()) {
                String prefix = plugin.getConfig().getString("prefix", "");
                disabledMsg = plugin.translateHexColorCodes(prefix + "&cThis feature is currently disabled!");
            }
            sender.sendMessage(disabledMsg);
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessage("messages.only-players"));
                return true;
            }

            Player player = (Player) sender;

            // 1. SET (SPAWN LOCATION)
            if (sub.equals("set") || sub.equals("ayarla")) {
                if (!player.hasPermission("veraxcore.spawn.set")) {
                    player.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                plugin.saveLocationToConfig("spawn-location", player.getLocation());
                player.sendMessage(plugin.getMessage("messages.spawn-set-success"));
                return true;
            } 
            
            // 2. SETFIRST (FIRST JOIN & RESPAWN LOCATION)
            else if (sub.equals("setfirst") || sub.equals("ilk-ayarla")) {
                if (!player.hasPermission("veraxcore.spawn.setfirst")) {
                    player.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                plugin.saveLocationToConfig("first-spawn-location", player.getLocation());
                player.sendMessage(plugin.getMessage("messages.first-spawn-set-success"));
                return true;
            } 
            
            // 3. REMOVE / DELETE (DELETE SPAWN LOCATION)
            else if (sub.equals("sil") || sub.equals("remove") || sub.equals("delete")) {
                if (!player.hasPermission("veraxcore.spawn.remove")) {
                    player.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                Location loc = plugin.getLocationFromConfig("spawn-location");
                if (loc == null) {
                    player.sendMessage(plugin.getMessage("messages.spawn-not-set"));
                    return true;
                }
                
                plugin.clearLocationFromConfig("spawn-location");
                player.sendMessage(plugin.getMessage("messages.spawn-deleted-success"));
                return true;
            } 
            
            // 4. REMOVEFIRST (DELETE FIRST JOIN SPAWN LOCATION)
            else if (sub.equals("firstsil") || sub.equals("removefirst") || sub.equals("ilk-sil")) {
                if (!player.hasPermission("veraxcore.spawn.remove")) {
                    player.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                Location locFirst = plugin.getLocationFromConfig("first-spawn-location");
                if (locFirst == null) {
                    player.sendMessage(plugin.getMessage("messages.spawn-not-set"));
                    return true;
                }
                
                plugin.clearLocationFromConfig("first-spawn-location");
                player.sendMessage(plugin.getMessage("messages.first-spawn-deleted-success"));
                return true;
            }
        }

        // --- DIRECT /SPAWN TELEPORT LOGIC ---
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = (Player) sender;
        Location spawnLoc = plugin.getLocationFromConfig("spawn-location");
        if (spawnLoc == null) {
            player.sendMessage(plugin.getMessage("messages.spawn-not-set"));
            return true;
        }

        // Instant teleport if player has bypass permission
        if (player.hasPermission("veraxcore.spawn.bypass")) {
            cancelTeleportQuietly(player.getUniqueId());
            plugin.safeTeleport(player, spawnLoc);
            player.sendMessage(plugin.getMessage("teleport-messages.teleport-success"));
            playSound(player, plugin, "sounds.success");
            return true;
        }

        // Cancel previously active teleport task if any
        cancelTeleportQuietly(player.getUniqueId());

        int delay = plugin.getConfig().getInt("teleport-delay", 5);

        String mainTitle = plugin.getRawMessage("spawn-title-settings.title-moving");
        String subTitleFormat = plugin.getRawMessage("spawn-title-settings.subtitle-teleporting");

        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeTeleports.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    activeTeleports.remove(player.getUniqueId());
                    Location targetLoc = plugin.getLocationFromConfig("spawn-location");
                    if (targetLoc == null) targetLoc = spawnLoc;

                    plugin.safeTeleport(player, targetLoc);
                    player.sendMessage(plugin.getMessage("teleport-messages.teleport-success"));
                    playSound(player, plugin, "sounds.success");
                    player.sendTitle("", "", 0, 1, 0);
                    cancel();
                    return;
                }

                String currentSub = subTitleFormat
                        .replace("%remaining_seconds%", String.valueOf(remaining))
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%kalan_saniye%", String.valueOf(remaining));
                player.sendTitle(
                    plugin.translateHexColorCodes(mainTitle), 
                    plugin.translateHexColorCodes(currentSub), 
                    0, 25, 5
                );
                playSound(player, plugin, "sounds.countdown");

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeTeleports.put(player.getUniqueId(), task);

        return true;
    }

    private static void playSound(Player player, VeraxCore plugin, String soundPath) {
        String soundName = plugin.getConfig().getString(soundPath, "");
        if (!soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        // If module is disabled, do not suggest anything during tab completion
        if (!plugin.getConfig().getBoolean("modules.spawn", true)) {
            return completions; 
        }

        if (args.length == 1 && (sender.hasPermission("veraxcore.spawn.set") || sender.hasPermission("veraxcore.spawn.setfirst") || sender.hasPermission("veraxcore.spawn.remove"))) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("veraxcore.spawn.set")) subCommands.add("set");
            if (sender.hasPermission("veraxcore.spawn.setfirst")) subCommands.add("setfirst");
            if (sender.hasPermission("veraxcore.spawn.remove")) subCommands.add("remove");

            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }
}

package com.verax.veraxcore.listeners;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.hooks.LuckPermsHook;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityListener implements Listener {

    private final VeraxCore plugin;
    public static final Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();
    public static final Set<UUID> onlineStaffCache = ConcurrentHashMap.newKeySet();

    public ActivityListener(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isEssentialsAfkStatic(Player player) {
        if (player == null) return false;
        try {
            if (player.hasMetadata("essentials:afk")) {
                return player.getMetadata("essentials:afk").get(0).asBoolean();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isEssentialsAfk(Player player) {
        return isEssentialsAfkStatic(player);
    }

    public static boolean hasStaffRankStatic(VeraxCore plugin, Player player) {
        if (player == null || !player.isOnline()) return false;

        if (player.hasPermission("veraxcore.stafftime.view") || player.hasPermission("veraxcore.yetkilisure.gor")) {
            return true;
        }

        FileConfiguration config = plugin.getConfig();
        List<String> rawRanks = config.getStringList("allowed-ranks");
        if (rawRanks == null || rawRanks.isEmpty()) {
            rawRanks = config.getStringList("izin-verilen-ranklar");
        }
        if (rawRanks == null || rawRanks.isEmpty()) {
            return false;
        }
        final List<String> ranks = rawRanks;

        // 1. Standard Bukkit / LuckPerms group.<rank> permission check
        for (String rank : ranks) {
            if (rank != null && !rank.trim().isEmpty() && player.hasPermission("group." + rank.trim().toLowerCase())) {
                return true;
            }
        }

        // 2. LuckPerms API check (Primary group and nodes)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    String primaryGroup = user.getPrimaryGroup();
                    if (primaryGroup != null) {
                        for (String rank : ranks) {
                            if (primaryGroup.equalsIgnoreCase(rank.trim())) {
                                return true;
                            }
                        }
                    }

                    boolean nodeMatch = user.getNodes().stream().anyMatch(node -> {
                        String key = node.getKey().toLowerCase();
                        if (key.equals("veraxcore.stafftime.view") || key.equals("veraxcore.yetkilisure.gor")) return true;
                        if (key.startsWith("group.")) {
                            String groupName = key.substring(6);
                            return ranks.stream().anyMatch(r -> r.equalsIgnoreCase(groupName));
                        }
                        return false;
                    });
                    if (nodeMatch) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean hasStaffRank(Player player) {
        return hasStaffRankStatic(plugin, player);
    }

    /**
     * Synchronizes the staff status of an online player immediately.
     * When a player receives permissions/roles while in-game, tracking starts immediately without requiring reconnect.
     */
    public static void syncStaffState(VeraxCore plugin, Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        boolean isStaff = hasStaffRankStatic(plugin, player);
        boolean wasStaff = onlineStaffCache.contains(uuid);

        if (isStaff) {
            if (!wasStaff) {
                onlineStaffCache.add(uuid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDatabaseManager().updateUsername(uuid, player.getName());
                });
            }
            if (!isEssentialsAfkStatic(player)) {
                if (!loginTimes.containsKey(uuid)) {
                    loginTimes.put(uuid, System.currentTimeMillis());
                }
            }
        } else {
            if (wasStaff) {
                onlineStaffCache.remove(uuid);
                if (loginTimes.containsKey(uuid)) {
                    long joinTime = loginTimes.remove(uuid);
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
            }
        }
    }

    /**
     * Synchronizes staff status for all online players.
     */
    public static void syncAllOnlinePlayers(VeraxCore plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncStaffState(plugin, p);
        }
    }

    /**
     * Registers LuckPerms event listener hook if LuckPerms is installed.
     */
    public static void registerLuckPermsHook(VeraxCore plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            LuckPermsHook.register(plugin);
        }
    }

    /**
     * Periodic task to sync staff permissions automatically in case permissions change while players are online.
     */
    public static void startStaffSyncTask(VeraxCore plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("modules.staff-time", plugin.getConfig().getBoolean("modules.yetkili-sure", true))) {
                return;
            }
            syncAllOnlinePlayers(plugin);
        }, 600L, 600L);
    }

    private void checkAfkAndStartTracking(Player player) {
        UUID uuid = player.getUniqueId();
        if (!onlineStaffCache.contains(uuid)) {
            if (hasStaffRank(player)) {
                syncStaffState(plugin, player);
            } else {
                return;
            }
        }

        if (!loginTimes.containsKey(uuid) && !isEssentialsAfkStatic(player)) {
            loginTimes.put(uuid, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.getPlayerSettingsManager().loadPlayerSettingsAsync(uuid);

        syncStaffState(plugin, player);

        // Update notification (For Admins / OPs)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                com.verax.veraxcore.utils.UpdateChecker.notifyPlayerIfUpdateAvailable(player, plugin);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getPlayerSettingsManager().unloadPlayerSettings(uuid);
        onlineStaffCache.remove(uuid);

        if (loginTimes.containsKey(uuid)) {
            long joinTime = loginTimes.remove(uuid);
            int sessionSeconds = (int) ((System.currentTimeMillis() - joinTime) / 1000);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().updateUsername(uuid, player.getName());
                if (sessionSeconds > 0) {
                    plugin.getDatabaseManager().addSeconds(uuid, "daily", sessionSeconds);
                    plugin.getDatabaseManager().addSeconds(uuid, "weekly", sessionSeconds);
                    plugin.getDatabaseManager().addSeconds(uuid, "monthly", sessionSeconds);
                    plugin.getDatabaseManager().addSeconds(uuid, "total", sessionSeconds);
                }
            });
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        String message = event.getMessage().toLowerCase();

        if (message.equals("/afk") || message.startsWith("/afk ")) {
            UUID uuid = player.getUniqueId();
            if (onlineStaffCache.contains(uuid)) {
                if (!isEssentialsAfkStatic(player)) {
                    if (loginTimes.containsKey(uuid)) {
                        long joinTime = loginTimes.remove(uuid);
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
                    loginTimes.put(uuid, System.currentTimeMillis());
                }
            } else {
                loginTimes.remove(uuid);
            }
        } else {
            checkAfkAndStartTracking(player);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        checkAfkAndStartTracking(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // Return immediately with zero overhead for non-staff players
        if (!onlineStaffCache.contains(uuid)) return;

        // Only check when block coordinates change
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            checkAfkAndStartTracking(player);
        }
    }

    public static void updateOnlinePlayers(VeraxCore plugin) {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean isStaff = hasStaffRankStatic(plugin, player);

            if (isStaff) {
                onlineStaffCache.add(uuid);

                if (isEssentialsAfkStatic(player)) {
                    if (loginTimes.containsKey(uuid)) {
                        long joinTime = loginTimes.remove(uuid);
                        int sessionSeconds = (int) ((now - joinTime) / 1000);
                        if (sessionSeconds > 0) {
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                plugin.getDatabaseManager().addSeconds(uuid, "daily", sessionSeconds);
                                plugin.getDatabaseManager().addSeconds(uuid, "weekly", sessionSeconds);
                                plugin.getDatabaseManager().addSeconds(uuid, "monthly", sessionSeconds);
                                plugin.getDatabaseManager().addSeconds(uuid, "total", sessionSeconds);
                            });
                        }
                    }
                    continue; 
                }

                if (!loginTimes.containsKey(uuid)) {
                    loginTimes.put(uuid, now);
                } else {
                    long joinTime = loginTimes.get(uuid);
                    int sessionSeconds = (int) ((now - joinTime) / 1000);
                    if (sessionSeconds > 0) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            plugin.getDatabaseManager().addSeconds(uuid, "daily", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "weekly", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "monthly", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "total", sessionSeconds);
                        });
                        loginTimes.put(uuid, now);
                    }
                }
            } else {
                onlineStaffCache.remove(uuid);
                if (loginTimes.containsKey(uuid)) {
                    long joinTime = loginTimes.remove(uuid);
                    int sessionSeconds = (int) ((now - joinTime) / 1000);
                    if (sessionSeconds > 0) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            plugin.getDatabaseManager().addSeconds(uuid, "daily", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "weekly", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "monthly", sessionSeconds);
                            plugin.getDatabaseManager().addSeconds(uuid, "total", sessionSeconds);
                        });
                    }
                }
            }
        }
    }
}

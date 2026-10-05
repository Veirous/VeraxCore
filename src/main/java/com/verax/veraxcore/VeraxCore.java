package com.verax.veraxcore;

import com.verax.veraxcore.commands.*;
import com.verax.veraxcore.listeners.*;
import com.verax.veraxcore.managers.DatabaseManager;
import com.verax.veraxcore.managers.LanguageManager;
import com.verax.veraxcore.managers.MaintenanceManager;
import com.verax.veraxcore.managers.PlayerSettingsManager;
import com.verax.veraxcore.managers.PVManager;
import com.verax.veraxcore.models.VaultContentHolder;
import com.verax.veraxcore.systems.AutoAnnouncementSystem;
import com.verax.veraxcore.systems.AutoRestartSystem;
import com.verax.veraxcore.systems.DiscordLogSender;
import com.verax.veraxcore.systems.VerificationSystem;
import com.verax.veraxcore.utils.AdventureUtil;
import com.verax.veraxcore.utils.ConfigUpdater;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import com.verax.veraxcore.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VeraxCore extends JavaPlugin {

    private static VeraxCore instance;
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private ChatControlListener chatControlListener;
    private AutoAnnouncementSystem autoAnnouncementSystem;
    private PVManager pvManager;
    private MaintenanceManager maintenanceManager;
    private AutoRestartSystem autoRestartSystem;
    private VerificationSystem verificationSystem;
    private PlayerSettingsManager playerSettingsManager;
    private FileConfiguration customConfig;

    // Trackers for automatic scheduled log & reset checks
    private int lastDailyResetDay = -1;
    private int lastWeeklyResetWeek = -1;
    private int lastMonthlyResetMonth = -1;

    private final Set<UUID> unblockableTeleports = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;

        // Configuration setup and smart default updates (preserves user settings)
        setupConfig();

        // Multi-language manager
        languageManager = new LanguageManager(this);

        // Database setup
        databaseManager = new DatabaseManager(this);
        databaseManager.setupDatabase();

        // Player settings manager
        playerSettingsManager = new PlayerSettingsManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            try {
                getServer().getPluginManager().registerEvents(new EssentialsAfkListener(this), this);
                getLogger().info("[AFK] Essentials hook successfully registered.");
            } catch (Throwable t) {
                getLogger().warning("[AFK] Could not hook into Essentials: " + t.getMessage());
            }
        }
        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);

        this.chatControlListener = new ChatControlListener(this);
        getServer().getPluginManager().registerEvents(this.chatControlListener, this);

        // Command registrations
        StaffPlaytimeCommand staffPlaytimeCommand = new StaffPlaytimeCommand(this);
        if (getCommand("yetkilisüre") != null) {
            getCommand("yetkilisüre").setExecutor(staffPlaytimeCommand);
            getCommand("yetkilisüre").setTabCompleter(staffPlaytimeCommand);
        }

        if (getCommand("ys") != null) {
            StaffChatCommand staffChatCommand = new StaffChatCommand(this);
            getCommand("ys").setExecutor(staffChatCommand);
            getCommand("ys").setTabCompleter(staffChatCommand);
        }

        ChatCommand chatCommand = new ChatCommand(this, this.chatControlListener);
        if (getCommand("sohbet") != null) {
            getCommand("sohbet").setExecutor(chatCommand);
            getCommand("sohbet").setTabCompleter(chatCommand);
        }

        AnnouncementCommand announcementCommand = new AnnouncementCommand(this);
        if (getCommand("duyuru") != null) {
            getCommand("duyuru").setExecutor(announcementCommand);
            getCommand("duyuru").setTabCompleter(announcementCommand);
        }

        SpawnCommand spawnCommand = new SpawnCommand(this);
        if (getCommand("spawn") != null) {
            getCommand("spawn").setExecutor(spawnCommand);
            getCommand("spawn").setTabCompleter(spawnCommand);
        }

        VeraxCoreCommand veraxCoreCommand = new VeraxCoreCommand(this);
        if (getCommand("veraxcore") != null) {
            getCommand("veraxcore").setExecutor(veraxCoreCommand);
            getCommand("veraxcore").setTabCompleter(veraxCoreCommand);
        }

        if (getCommand("rapor") != null) {
            ReportCommand reportCommand = new ReportCommand(this);
            getCommand("rapor").setExecutor(reportCommand);
            getCommand("rapor").setTabCompleter(reportCommand);
        }

        // Trade System
        TradeChatListener tradeChat = new TradeChatListener(this);
        TradeMenuListener tradeMenu = new TradeMenuListener(this, tradeChat);
        getServer().getPluginManager().registerEvents(tradeChat, this);
        getServer().getPluginManager().registerEvents(tradeMenu, this);
        if (getCommand("ticaret") != null) {
            TradeCommand tradeCommand = new TradeCommand(this, tradeMenu);
            getCommand("ticaret").setExecutor(tradeCommand);
            getCommand("ticaret").setTabCompleter(tradeCommand);
        }

        // Auto Announcement Command
        AutoAnnouncementCommand autoAnnouncementCommand = new AutoAnnouncementCommand(this);
        if (getCommand("otoduyuru") != null) {
            getCommand("otoduyuru").setExecutor(autoAnnouncementCommand);
            getCommand("otoduyuru").setTabCompleter(autoAnnouncementCommand);
        }

        // Security Verification System
        if (getConfig().getBoolean("modules.verification", getConfig().getBoolean("modules.dogrulama", true))) {
            this.verificationSystem = new VerificationSystem(this);
            getServer().getPluginManager().registerEvents(this.verificationSystem, this);
            if (getCommand("doğrula") != null) {
                getCommand("doğrula").setExecutor(this.verificationSystem);
                getCommand("doğrula").setTabCompleter(this.verificationSystem);
            }
            if (getCommand("verify") != null) {
                getCommand("verify").setExecutor(this.verificationSystem);
                getCommand("verify").setTabCompleter(this.verificationSystem);
            }
        }

        // Trash Bin Command
        if (getConfig().getBoolean("modules.trash", getConfig().getBoolean("modules.cop", true))) {
            TrashCommand trashCommand = new TrashCommand(this);
            if (getCommand("çöp") != null) {
                getCommand("çöp").setExecutor(trashCommand);
                getCommand("çöp").setTabCompleter(trashCommand);
            }
            if (getCommand("trash") != null) {
                getCommand("trash").setExecutor(trashCommand);
                getCommand("trash").setTabCompleter(trashCommand);
            }
        }

        // Player Vault (PV) System
        if (getConfig().getBoolean("modules.pv", true)) {
            this.pvManager = new PVManager(this);
            getServer().getPluginManager().registerEvents(new PVListener(this, this.pvManager), this);

            if (getCommand("pv") != null) {
                PVCommand pvCmd = new PVCommand(this, this.pvManager);
                getCommand("pv").setExecutor(pvCmd);
                getCommand("pv").setTabCompleter(pvCmd);
            }
            if (getCommand("pvadmin") != null) {
                PVAdminCommand pvAdminCmd = new PVAdminCommand(this, this.pvManager);
                getCommand("pvadmin").setExecutor(pvAdminCmd);
                getCommand("pvadmin").setTabCompleter(pvAdminCmd);
            }
        }

        // Maintenance System
        if (getConfig().getBoolean("modules.maintenance", getConfig().getBoolean("modules.bakim", true))) {
            this.maintenanceManager = new MaintenanceManager(this);
            getServer().getPluginManager().registerEvents(new MaintenanceListener(this, this.maintenanceManager), this);

            MaintenanceCommand maintenanceCommand = new MaintenanceCommand(this, this.maintenanceManager);
            if (getCommand("bakım") != null) {
                getCommand("bakım").setExecutor(maintenanceCommand);
                getCommand("bakım").setTabCompleter(maintenanceCommand);
            }
            if (getCommand("maintenance") != null) {
                getCommand("maintenance").setExecutor(maintenanceCommand);
                getCommand("maintenance").setTabCompleter(maintenanceCommand);
            }
        }

        // Auto Chat Announcement System
        this.autoAnnouncementSystem = new AutoAnnouncementSystem(this);
        this.autoAnnouncementSystem.start();

        // Auto Restart System
        this.autoRestartSystem = new AutoRestartSystem(this);
        this.autoRestartSystem.start();

        // Synchronize active staff players and start tracking
        ActivityListener.syncAllOnlinePlayers(this);
        ActivityListener.registerLuckPermsHook(this);
        ActivityListener.startStaffSyncTask(this);

        // Record initial values according to configured timezone
        ZoneId zoneId = getValidZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        lastDailyResetDay = now.getDayOfYear();
        lastWeeklyResetWeek = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        lastMonthlyResetMonth = now.getMonthValue();

        startDayCheckTask();
        startPVAutoSaveTask();
        startStaffTimeAutoSaveTask();

        // Startup Banner
        Bukkit.getConsoleSender().sendMessage("§d=====================================================");
        Bukkit.getConsoleSender().sendMessage("§b  ██╗   ██╗███████╗██████╗  █████╗ ██╗  ██╗");
        Bukkit.getConsoleSender().sendMessage("§b  ██║   ██║██╔════╝██╔══██╗██╔══██╗╚██╗██╔╝");
        Bukkit.getConsoleSender().sendMessage("§b  ██║   ██║█████╗  ██████╔╝███████║ ╚███╔╝ ");
        Bukkit.getConsoleSender().sendMessage("§b  ╚██╗ ██╔╝██╔══╝  ██╔══██╗██╔══██║ ██╔██╗ ");
        Bukkit.getConsoleSender().sendMessage("§b   ╚████╔╝ ███████╗██║  ██║██║  ██║██╔╝ ██╗");
        Bukkit.getConsoleSender().sendMessage("§b    ╚═══╝  ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝");
        Bukkit.getConsoleSender().sendMessage("§d=====================================================");
        Bukkit.getConsoleSender().sendMessage("§f [§a✔§f] §bVeraxCore §aACTIVE!");
        Bukkit.getConsoleSender().sendMessage("§e [i] Timezone: §f" + zoneId.getId());
        Bukkit.getConsoleSender().sendMessage("§e [i] Developer: §fVeraxDev");
        Bukkit.getConsoleSender().sendMessage("§d=====================================================");

        // Update Checker (SpigotMC: 138126)
        new UpdateChecker(this).logToConsole();
    }

    @Override
    public void onDisable() {
        if (maintenanceManager != null) {
            maintenanceManager.onDisable();
        }

        if (autoAnnouncementSystem != null) {
            autoAnnouncementSystem.stop();
        }

        if (autoRestartSystem != null) {
            autoRestartSystem.stop();
        }

        if (verificationSystem != null) {
            verificationSystem.onDisable();
        }

        if (pvManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (p.getOpenInventory() != null && p.getOpenInventory().getTopInventory() != null
                            && p.getOpenInventory().getTopInventory().getHolder() instanceof VaultContentHolder) {
                        p.closeInventory();
                    }
                } catch (Exception ignored) {}
            }
            pvManager.saveAllActiveVaultsSync();
        }

        if (databaseManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    UUID uuid = p.getUniqueId();
                    if (ActivityListener.loginTimes.containsKey(uuid)) {
                        long loginTime = ActivityListener.loginTimes.get(uuid);
                        int seconds = (int) ((System.currentTimeMillis() - loginTime) / 1000);
                        if (seconds > 0) {
                            databaseManager.addSeconds(uuid, "daily", seconds);
                            databaseManager.addSeconds(uuid, "weekly", seconds);
                            databaseManager.addSeconds(uuid, "monthly", seconds);
                            databaseManager.addSeconds(uuid, "total", seconds);
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Error saving player session on disable for " + p.getName() + ": " + e.getMessage());
                }
            }
            databaseManager.closeConnection();
        }

        DiscordWebhookSender.close();

        Bukkit.getConsoleSender().sendMessage("§c=====================================================");
        Bukkit.getConsoleSender().sendMessage("§c  [✘] VeraxCore Successfully DISABLED!");
        Bukkit.getConsoleSender().sendMessage("§7  Data saved and connections safely closed.");
        Bukkit.getConsoleSender().sendMessage("§c=====================================================");
    }

    // Centralized debug log method
    public void logDebug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // --- HELPER METHODS ---

    public String getRawMessage(String path) {
        if (languageManager != null) {
            return languageManager.getRawMessage(path);
        }
        return getConfig().getString(path, "");
    }

    public String getMessage(String path) {
        return getMessage(path, null);
    }

    public String getMessage(String path, String playerName) {
        if (languageManager != null) {
            return languageManager.getMessage(path, playerName);
        }
        FileConfiguration config = getConfig();
        String msg = config.getString(path, "");
        if (msg == null || msg.isEmpty()) return "";

        String prefix = config.getString("prefix", "&#1b4fff[&bVerax&#1b4fff] ");
        msg = msg.replace("<prefix>", prefix).replace("%prefix%", prefix);

        if (playerName != null) {
            msg = msg.replace("{player}", playerName).replace("%player%", playerName);
        }

        return translateHexColorCodes(msg);
    }

    /**
     * Returns message as Paper Adventure Component (MiniMessage, Gradient, Hover, Click support).
     */
    public net.kyori.adventure.text.Component getMessageComponent(String path) {
        return getMessageComponent(path, null);
    }

    public net.kyori.adventure.text.Component getMessageComponent(String path, String playerName) {
        if (languageManager != null) {
            return languageManager.getMessageComponent(path, playerName);
        }
        String raw = getMessage(path, playerName);
        return AdventureUtil.parseComponent(raw);
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path) {
        sendMessage(sender, path, null);
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path, String playerName) {
        if (sender == null) return;
        AdventureUtil.sendMessage(sender, getMessageComponent(path, playerName));
    }

    public String translateHexColorCodes(String message) {
        if (message == null) return "";
        if (languageManager != null) {
            return languageManager.translateHexColorCodes(message);
        }
        final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + group).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    // --- LOCATION OPERATIONS ---

    public Location getLocationFromConfig(String path) {
        if (!getConfig().contains(path) || getConfig().getString(path + ".world", "").isEmpty()) return null;

        String worldName = getConfig().getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logDebug("Warning: World '" + worldName + "' is not loaded or could not be found!");
            return null;
        }

        return new Location(
                world,
                getConfig().getDouble(path + ".x"),
                getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"),
                (float) getConfig().getDouble(path + ".yaw"),
                (float) getConfig().getDouble(path + ".pitch")
        );
    }

    public void saveLocationToConfig(String path, Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        getConfig().set(path + ".world", loc.getWorld().getName());
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", loc.getYaw());
        getConfig().set(path + ".pitch", loc.getPitch());
        saveConfigSafely();
    }

    public void clearLocationFromConfig(String path) {
        getConfig().set(path + ".world", "");
        getConfig().set(path + ".x", 0.0);
        getConfig().set(path + ".y", 0.0);
        getConfig().set(path + ".z", 0.0);
        getConfig().set(path + ".yaw", 0.0);
        getConfig().set(path + ".pitch", 0.0);
        saveConfigSafely();
    }

    public boolean isUnblockableTeleport(UUID uuid) {
        return unblockableTeleports.contains(uuid);
    }

    public void setUnblockableTeleport(UUID uuid, boolean unblockable) {
        if (unblockable) {
            unblockableTeleports.add(uuid);
        } else {
            unblockableTeleports.remove(uuid);
        }
    }

    /**
     * Safe Teleportation Method:
     * Ensures target chunk is loaded, ejects passengers and vehicles,
     * and prevents cancellation by other plugins during bypass period.
     */
    public boolean safeTeleport(Player player, Location loc) {
        if (player == null || !player.isOnline() || loc == null || loc.getWorld() == null) {
            return false;
        }

        World world = loc.getWorld();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ, true);
        }

        // Force eject vehicle or any passenger entities
        try {
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            for (org.bukkit.entity.Entity passenger : new ArrayList<>(player.getPassengers())) {
                try {
                    player.removePassenger(passenger);
                } catch (Throwable ignored) {}
            }
            player.eject();
        } catch (Throwable ignored) {}

        UUID uuid = player.getUniqueId();
        unblockableTeleports.add(uuid);

        boolean success = false;
        try {
            success = player.teleport(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Throwable ignored) {}

        if (!success) {
            try {
                java.lang.reflect.Method teleportAsyncMethod = player.getClass().getMethod(
                        "teleportAsync", Location.class, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class
                );
                teleportAsyncMethod.invoke(player, loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                success = true;
            } catch (Throwable t) {
                try {
                    player.teleport(loc);
                } catch (Throwable ignored) {}
            }
        }

        // Remove unblockable teleport lock after 5 ticks
        getServer().getScheduler().runTaskLater(this, () -> unblockableTeleports.remove(uuid), 5L);
        return true;
    }

    public ZoneId getValidZoneId() {
        String tz = getConfig().getString("timezone", "Europe/Istanbul");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            logDebug("Timezone in config '" + tz + "' is invalid! Using default Europe/Istanbul.");
            return ZoneId.of("Europe/Istanbul");
        }
    }

    /**
     * Runs every 30 seconds (600 ticks).
     * Triggers daily, weekly, and monthly logs according to the configured timezone.
     *
     * Daily  -> Sent every midnight at 00:00 (on day transition)
     * Weekly -> Sent every Monday at 00:00
     * Monthly -> Sent on the 1st of every month at 00:00
     */
    private void startDayCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                ZoneId zoneId = getValidZoneId();
                ZonedDateTime currentTime = ZonedDateTime.now(zoneId);
                int todayDay = currentTime.getDayOfYear();
                int currentWeek = currentTime.get(WeekFields.ISO.weekOfWeekBasedYear());
                int currentMonth = currentTime.getMonthValue();

                // ── DAILY LOG & RESET ──────────────────────────────────────────────
                if (todayDay != lastDailyResetDay) {
                    lastDailyResetDay = todayDay;
                    getLogger().info("[AutoLog] Day change detected (" + zoneId.getId() + ") -> Starting daily tasks...");

                    if (getConfig().getBoolean("auto-log.daily.enabled", getConfig().getBoolean("oto-log.gunluk.enabled", true))) {
                        getLogger().info("[AutoLog] Sending daily log to Discord...");
                        boolean success = DiscordLogSender.sendStaffReport(this, "daily");
                        getLogger().info("[AutoLog] Daily log dispatch: " + (success ? "SUCCESSFUL ✔" : "FAILED ✘"));
                    } else {
                        getLogger().info("[AutoLog] Daily log is disabled (auto-log.daily.enabled=false).");
                    }

                    // Reset session counters for all online staff
                    Bukkit.getScheduler().runTask(this, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (ActivityListener.loginTimes.containsKey(p.getUniqueId())) {
                                ActivityListener.loginTimes.put(p.getUniqueId(), System.currentTimeMillis());
                            }
                        }
                    });

                    databaseManager.clearDailySeconds();
                    getLogger().info("[AutoLog] Daily database activity times reset.");
                }

                // ── WEEKLY LOG & RESET (Every Monday) ───────────────────────────
                if (currentWeek != lastWeeklyResetWeek && currentTime.getDayOfWeek() == DayOfWeek.MONDAY) {
                    lastWeeklyResetWeek = currentWeek;

                    if (getConfig().getBoolean("auto-log.weekly.enabled", getConfig().getBoolean("oto-log.haftalik.enabled", true))) {
                        logDebug("[AutoLog] " + zoneId.getId() + " timezone -> Sending weekly log to Discord...");
                        DiscordLogSender.sendStaffReport(this, "weekly");
                    }

                    if (getConfig().getBoolean("auto-log.weekly.reset", getConfig().getBoolean("oto-log.haftalik.sifirla", true))) {
                        databaseManager.clearSecondsByType("weekly");
                        logDebug("[AutoLog] Weekly database activity times reset.");
                    }
                }

                // ── MONTHLY LOG & RESET (1st day of month) ──────────────────────
                if (currentMonth != lastMonthlyResetMonth && currentTime.getDayOfMonth() == 1) {
                    lastMonthlyResetMonth = currentMonth;

                    if (getConfig().getBoolean("auto-log.monthly.enabled", getConfig().getBoolean("oto-log.aylik.enabled", true))) {
                        logDebug("[AutoLog] " + zoneId.getId() + " timezone -> Sending monthly log to Discord...");
                        DiscordLogSender.sendStaffReport(this, "monthly");
                    }

                    if (getConfig().getBoolean("auto-log.monthly.reset", getConfig().getBoolean("oto-log.aylik.sifirla", true))) {
                        databaseManager.clearSecondsByType("monthly");
                        logDebug("[AutoLog] Monthly database activity times reset.");
                    }
                }

            } catch (Exception e) {
                getLogger().severe("Error during day check: " + e.getMessage());
                e.printStackTrace();
            }
        }, 100L, 600L); // Start after 100 ticks, repeat every 600 ticks (30s)
    }

    /**
     * Runs every 5 minutes (6000 ticks).
     * Snapshots open Player Vault (VaultContentHolder) inventories and saves asynchronously to DB.
     * Prevents PV item loss in unexpected server crashes.
     */
    private void startPVAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("modules.pv", true)) return;

            // Main thread: safely snapshot inventory contents
            List<Object[]> snapshots = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                org.bukkit.inventory.InventoryView view = p.getOpenInventory();
                if (view == null) continue;

                org.bukkit.inventory.Inventory topInv = view.getTopInventory();
                if (topInv == null || !(topInv.getHolder() instanceof VaultContentHolder holder)) continue;

                org.bukkit.inventory.ItemStack[] contents = topInv.getContents();
                org.bukkit.inventory.ItemStack[] snapshot = new org.bukkit.inventory.ItemStack[contents.length];
                for (int i = 0; i < contents.length; i++) {
                    snapshot[i] = contents[i] != null ? contents[i].clone() : null;
                }
                snapshots.add(new Object[]{holder.getTargetUuid(), holder.getVaultId(), snapshot});
            }

            if (snapshots.isEmpty()) return;

            // Save snapshots to DB asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                for (Object[] entry : snapshots) {
                    UUID uuid = (UUID) entry[0];
                    int vaultId = (int) entry[1];
                    org.bukkit.inventory.ItemStack[] items = (org.bukkit.inventory.ItemStack[]) entry[2];
                    databaseManager.saveVault(uuid, vaultId, items);
                }
                logDebug("[PV-AutoSave] " + snapshots.size() + " open PVs saved.");
            });
        }, 3000L, 3000L);
    }

    /**
     * Runs every 5 minutes (6000 ticks).
     * Automatically backs up online staff playtime to the database.
     */
    private void startStaffTimeAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("modules.staff-time", getConfig().getBoolean("modules.yetkili-sure", true))) return;
            ActivityListener.updateOnlinePlayers(this);
            logDebug("[Staff-AutoSave] Online staff session times backed up to database.");
        }, 6000L, 6000L);
    }

    /**
     * Reconnects database if storage-type was changed during config reload.
     *
     * @param oldStorageType storage-type read before config reload
     */
    public void reloadDatabaseIfNeeded(String oldStorageType) {
        String newType = getConfig().getString("storage-type", "SQLITE").toUpperCase(java.util.Locale.ROOT);
        String oldType = (oldStorageType != null ? oldStorageType : "SQLITE").toUpperCase(java.util.Locale.ROOT);

        if (!oldType.equals(newType)) {
            getLogger().info("[Database] storage-type changed: " + oldType + " -> " + newType + ". Reconnecting...");
            if (databaseManager != null) {
                databaseManager.closeDatabase();
            }
            databaseManager = new DatabaseManager(this);
            databaseManager.setupDatabase();
            getLogger().info("[Database] Database successfully restarted (" + newType + ").");
        } else {
            getLogger().info("[Database] storage-type did not change (" + newType + "), database not restarted.");
        }
    }

    /**
     * Smart Configuration Setup:
     * Does not overwrite existing user settings.
     * Automatically adds missing keys and comments from template to existing files.
     */
    public void setupConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else {
            ConfigUpdater.update(this, "config.yml", configFile);
        }

        reloadConfig();

        if (customConfig == null) {
            getLogger().severe("Could not load config! Check config.yml file.");
        }
    }

    @Override
    public FileConfiguration getConfig() {
        if (customConfig == null) {
            reloadConfig();
        }
        return customConfig;
    }

    @Override
    public void reloadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            customConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            getLogger().severe("Error loading config.yml: " + e.getMessage());
        }
        if (languageManager != null) {
            languageManager.reload();
        }
        if (autoRestartSystem != null) {
            autoRestartSystem.start();
        }
        if (chatControlListener != null) {
            chatControlListener.reloadWordActions();
        }
        if (autoAnnouncementSystem != null) {
            autoAnnouncementSystem.reload();
        }
        if (verificationSystem != null) {
            verificationSystem.reload();
        }
    }

    public void saveConfigSafely() {
        try {
            getConfig().save(new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            getLogger().severe("Error saving config.yml: " + e.getMessage());
        }
    }

    public static VeraxCore getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public ChatControlListener getChatControlListener() { return chatControlListener; }
    public AutoAnnouncementSystem getAutoAnnouncementSystem() { return autoAnnouncementSystem; }
    public PVManager getPvManager() { return pvManager; }
    public MaintenanceManager getMaintenanceManager() { return maintenanceManager; }
    public AutoRestartSystem getAutoRestartSystem() { return autoRestartSystem; }
    public VerificationSystem getVerificationSystem() { return verificationSystem; }
    public PlayerSettingsManager getPlayerSettingsManager() { return playerSettingsManager; }
}
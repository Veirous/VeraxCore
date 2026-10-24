package com.verax.veraxcore.listeners;

import com.verax.veraxcore.models.TradeListing;
import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeChatListener implements Listener {

    private final VeraxCore plugin;
    private final Set<UUID> waitingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> pendingSlots = new ConcurrentHashMap<>();
    private final Map<Integer, TradeListing> activeTrades = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChatProcessTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastChatContent = new ConcurrentHashMap<>();
    private final Set<BossBar> activeBossBars = ConcurrentHashMap.newKeySet();
    private org.bukkit.scheduler.BukkitTask expirationTask;
    // Timestamp of the last trade creation (global cooldown)
    private volatile long lastTradeTimestamp = 0L;

    private boolean isDuplicateChat(UUID uuid, String rawMsg) {
        if (uuid == null || rawMsg == null) return false;
        long now = System.currentTimeMillis();
        Long lastTime = lastChatProcessTime.get(uuid);
        String lastMsg = lastChatContent.get(uuid);
        if (lastTime != null && (now - lastTime) < 500L && Objects.equals(lastMsg, rawMsg)) {
            return true;
        }
        lastChatProcessTime.put(uuid, now);
        lastChatContent.put(uuid, rawMsg);
        return false;
    }

    public TradeChatListener(VeraxCore plugin) {
        this.plugin = plugin;
        startExpirationTask();
    }

    // Getter for global last trade timestamp
    public long getLastTradeTimestamp() {
        return lastTradeTimestamp;
    }

    public boolean isGlobalCooldownActive() {
        long globalCdConfig = plugin.getConfig().contains("settings.trade-global-cooldown") 
                ? plugin.getConfig().getLong("settings.trade-global-cooldown") 
                : plugin.getConfig().getLong("settings.ticaret-global-cooldown", 30L);
        long globalCdMs = globalCdConfig > 1000 ? globalCdConfig : globalCdConfig * 1000L;
        if (globalCdMs <= 0) return false;
        return (System.currentTimeMillis() - lastTradeTimestamp) < globalCdMs;
    }

    public long getGlobalCooldownRemainingSecs() {
        long globalCdConfig = plugin.getConfig().contains("settings.trade-global-cooldown") 
                ? plugin.getConfig().getLong("settings.trade-global-cooldown") 
                : plugin.getConfig().getLong("settings.ticaret-global-cooldown", 30L);
        long globalCdMs = globalCdConfig > 1000 ? globalCdConfig : globalCdConfig * 1000L;
        long timePassed = System.currentTimeMillis() - lastTradeTimestamp;
        if (globalCdMs > 0 && timePassed < globalCdMs) {
            return Math.max(1, (globalCdMs - timePassed + 999) / 1000);
        }
        return 0L;
    }

    public long getPlayerCooldownRemaining(UUID uuid) {
        if (playerCooldowns.containsKey(uuid)) {
            long remaining = playerCooldowns.get(uuid) - System.currentTimeMillis();
            return Math.max(0L, remaining);
        }
        return 0L;
    }

    public void addWaitingPlayer(UUID uuid) { waitingPlayers.add(uuid); }
    public void setPendingSlot(UUID uuid, int slot) { pendingSlots.put(uuid, slot); }
    public Map<Integer, TradeListing> getActiveTrades() { return activeTrades; }

    public boolean hasActiveTrade(UUID uuid) {
        if (playerCooldowns.containsKey(uuid)) {
            return playerCooldowns.get(uuid) > System.currentTimeMillis();
        }
        return false;
    }

    private void startExpirationTask() {
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();

            activeTrades.entrySet().removeIf(entry -> entry.getValue().getExpiryTime() <= now);

            for (Map.Entry<UUID, Long> entry : playerCooldowns.entrySet()) {
                if (entry.getValue() <= now) {
                    UUID uuid = entry.getKey();
                    if (playerCooldowns.remove(uuid, entry.getValue())) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                String key = plugin.getLanguageManager().contains("messages.trade-expired") ? "messages.trade-expired" : "messages.ticaret-expired";
                                String msg = plugin.getMessage(key);
                                p.sendMessage(msg);
                            }
                        });
                    }
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        waitingPlayers.remove(uuid);
        pendingSlots.remove(uuid);
        lastChatProcessTime.remove(uuid);
        lastChatContent.remove(uuid);
    }

    public void cleanup() {
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }
        for (BossBar bar : activeBossBars) {
            try {
                if (bar != null) bar.removeAll();
            } catch (Throwable ignored) {}
        }
        activeBossBars.clear();
        waitingPlayers.clear();
        pendingSlots.clear();
        lastChatProcessTime.clear();
        lastChatContent.clear();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onModernChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingPlayers.contains(uuid)) return;

        event.setCancelled(true);
        String message = com.verax.veraxcore.utils.AdventureUtil.toPlainText(event.message());
        if (isDuplicateChat(uuid, message)) return;
        handleTradeChatMessage(player, message);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingPlayers.contains(uuid)) return;

        event.setCancelled(true);
        if (isDuplicateChat(uuid, event.getMessage())) return;
        handleTradeChatMessage(player, event.getMessage());
    }

    private void handleTradeChatMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
            waitingPlayers.remove(uuid);
            pendingSlots.remove(uuid);
            String key = plugin.getLanguageManager().contains("messages.trade-cancel") ? "messages.trade-cancel" : "messages.ticaret-iptal";
            String msg = plugin.getMessage(key);
            player.sendMessage(msg);
            return;
        }

        waitingPlayers.remove(uuid);
        int slot = pendingSlots.getOrDefault(uuid, -1);
        pendingSlots.remove(uuid);

        if (slot == -1) return;

        // Check if slot was taken by another player while waiting
        TradeListing existingTrade = activeTrades.get(slot);
        if (existingTrade != null && existingTrade.getExpiryTime() > System.currentTimeMillis()) {
            String key = plugin.getLanguageManager().contains("messages.trade-slot-taken") ? "messages.trade-slot-taken" : "messages.ticaret-slot-dolu";
            String msg = plugin.getMessage(key);
            if (msg.isEmpty()) msg = "§cThis trade slot has already been taken!";
            player.sendMessage(msg);
            return;
        }

        // Message length checks
        int minLength = plugin.getConfig().contains("settings.trade-min-length") 
                ? plugin.getConfig().getInt("settings.trade-min-length", 3)
                : plugin.getConfig().getInt("settings.ticaret-min-length", 3);
        int maxLength = plugin.getConfig().contains("settings.trade-max-length") 
                ? plugin.getConfig().getInt("settings.trade-max-length", 100)
                : plugin.getConfig().getInt("settings.ticaret-max-length", 100);

        if (message.length() < minLength) {
            waitingPlayers.add(uuid);
            pendingSlots.put(uuid, slot);
            String key = plugin.getLanguageManager().contains("messages.trade-too-short") ? "messages.trade-too-short" : "messages.ticaret-cok-kisa";
            String msg = plugin.getMessage(key)
                    .replace("%min%", String.valueOf(minLength))
                    .replace("<min>", String.valueOf(minLength))
                    .replace("%limit%", String.valueOf(minLength));
            if (msg.isEmpty()) msg = "§cTicaret mesajınız çok kısa! En az " + minLength + " karakter olmalıdır.";
            player.sendMessage(msg);
            return;
        }

        if (maxLength > 0 && message.length() > maxLength) {
            waitingPlayers.add(uuid);
            pendingSlots.put(uuid, slot);
            String key = plugin.getLanguageManager().contains("messages.trade-too-long") ? "messages.trade-too-long" : "messages.ticaret-cok-uzun";
            String msg = plugin.getMessage(key)
                    .replace("%max%", String.valueOf(maxLength))
                    .replace("<max>", String.valueOf(maxLength))
                    .replace("%limit%", String.valueOf(maxLength));
            if (msg.isEmpty()) msg = "§cTicaret mesajınız çok uzun! En fazla " + maxLength + " karakter olabilir.";
            player.sendMessage(msg);
            return;
        }

        // Global cooldown check
        if (isGlobalCooldownActive()) {
            long remainingSecs = getGlobalCooldownRemainingSecs();
            String key = plugin.getLanguageManager().contains("messages.trade-global-cooldown-msg") ? "messages.trade-global-cooldown-msg" : "messages.ticaret-global-cooldown-msg";
            String msg = plugin.getMessage(key)
                               .replace("<remaining>", String.valueOf(remainingSecs))
                               .replace("%remaining_seconds%", String.valueOf(remainingSecs))
                               .replace("%remaining%", String.valueOf(remainingSecs))
                               .replace("%kalan_saniye%", String.valueOf(remainingSecs));
            player.sendMessage(msg);
            return;
        }

        long cooldownConfig = plugin.getConfig().contains("settings.trade-cooldown") ? plugin.getConfig().getLong("settings.trade-cooldown") : plugin.getConfig().getLong("settings.ticaret-cooldown", 300L);
        long cooldownDuration = cooldownConfig > 1000 ? cooldownConfig : cooldownConfig * 1000L;
        long expiryTime = System.currentTimeMillis() + cooldownDuration;

        playerCooldowns.put(uuid, expiryTime);
        activeTrades.put(slot, new TradeListing(uuid, player.getName(), message, slot, expiryTime));
        // Update global cooldown timestamp
        lastTradeTimestamp = System.currentTimeMillis();

        // Send Discord trade webhook log
        sendDiscordTradeLog(player, message, slot);

        Bukkit.getScheduler().runTask(plugin, () -> {
            // BossBar settings
            String rawBossBarText = plugin.getLanguageManager().getString("trade-visuals.bossbar-text",
                    plugin.getLanguageManager().getString("ticaret-visuals.bossbar-text", "&#1BA1FF%owner% is Trading!"));
            String bossBarText = rawBossBarText.replace("%player%", player.getName())
                                               .replace("%owner%", player.getName())
                                               .replace("%mesaj%", message)
                                               .replace("%message%", message);

            String colorStr = plugin.getConfig().getString("trade-visuals.bossbar-color",
                    plugin.getConfig().getString("ticaret-visuals.bossbar-color", "BLUE"));
            BarColor barColor;
            try { barColor = BarColor.valueOf(colorStr.toUpperCase()); } catch (Exception e) { barColor = BarColor.BLUE; }

            String styleStr = plugin.getConfig().getString("trade-visuals.bossbar-style",
                    plugin.getConfig().getString("ticaret-visuals.bossbar-style", "SEGMENTED_12"));
            BarStyle barStyle;
            try { barStyle = BarStyle.valueOf(styleStr.toUpperCase()); } catch (Exception e) { barStyle = BarStyle.SEGMENTED_12; }

            int duration = plugin.getConfig().getInt("trade-visuals.bossbar-duration-seconds",
                    plugin.getConfig().getInt("ticaret-visuals.bossbar-duration-seconds", 10));

            boolean disableChat = plugin.getConfig().getBoolean("trade-toggle-settings.disable-chat", true);
            boolean disableSound = plugin.getConfig().getBoolean("trade-toggle-settings.disable-sound", true);
            boolean disableTitle = plugin.getConfig().getBoolean("trade-toggle-settings.disable-title", true);
            boolean disableBossbar = plugin.getConfig().getBoolean("trade-toggle-settings.disable-bossbar", true);

            BossBar bossBar = Bukkit.createBossBar(plugin.translateHexColorCodes(bossBarText), barColor, barStyle);
            activeBossBars.add(bossBar);
            for (Player online : Bukkit.getOnlinePlayers()) {
                boolean isTradeOn = plugin.getPlayerSettingsManager().isTradeEnabled(online.getUniqueId());
                if (isTradeOn || !disableBossbar) {
                    bossBar.addPlayer(online);
                }
            }
            bossBar.setProgress(1.0);

            // BossBar timer animation
            final int totalTicks = duration * 20;
            final int[] elapsedTicks = {0};

            new BukkitRunnable() {
                @Override
                public void run() {
                    elapsedTicks[0] += 2;
                    if (elapsedTicks[0] >= totalTicks) {
                        activeBossBars.remove(bossBar);
                        bossBar.removeAll();
                        cancel();
                        return;
                    }
                    double progress = 1.0 - ((double) elapsedTicks[0] / totalTicks);
                    bossBar.setProgress(Math.max(0.0, progress));
                }
            }.runTaskTimer(plugin, 0L, 2L);

            // Title announcement
            String rawTitleText = plugin.getLanguageManager().getString("trade-visuals.title",
                    plugin.getLanguageManager().getString("ticaret-visuals.title", ""));
            String rawSubtitleText = plugin.getLanguageManager().getString("trade-visuals.subtitle",
                    plugin.getLanguageManager().getString("ticaret-visuals.subtitle", "&#1BA1FF%owner% &fis Trading"));

            String titleText = rawTitleText.replace("%player%", player.getName())
                                           .replace("%owner%", player.getName())
                                           .replace("%mesaj%", message)
                                           .replace("%message%", message);
            String subtitleText = rawSubtitleText.replace("%player%", player.getName())
                                                 .replace("%owner%", player.getName())
                                                 .replace("%mesaj%", message)
                                                 .replace("%message%", message);

            for (Player online : Bukkit.getOnlinePlayers()) {
                boolean isTradeOn = plugin.getPlayerSettingsManager().isTradeEnabled(online.getUniqueId());
                if (isTradeOn || !disableTitle) {
                    online.sendTitle(plugin.translateHexColorCodes(titleText), plugin.translateHexColorCodes(subtitleText), 10, 60, 10);
                }
            }

            // Sound effect
            String soundName = plugin.getConfig().getString("sounds.trade-bell", "BLOCK_BELL_USE");
            try {
                Sound sound = Sound.valueOf(soundName);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    boolean isTradeOn = plugin.getPlayerSettingsManager().isTradeEnabled(online.getUniqueId());
                    if (isTradeOn || !disableSound) {
                        online.playSound(online.getLocation(), sound, 1.0f, 1.0f);
                    }
                }
            } catch (Exception ignored) {}

            // Chat announcement
            String keyBroadcast = plugin.getLanguageManager().contains("messages.trade-broadcast") ? "messages.trade-broadcast" : "messages.ticaret-broadcast";
            String rawBroadcast = plugin.getLanguageManager().getString(keyBroadcast, "");
            if (rawBroadcast != null && !rawBroadcast.isEmpty()) {
                String formattedMessage = rawBroadcast
                    .replace("%player%", player.getName())
                    .replace("%owner%", player.getName())
                    .replace("%message%", message)
                    .replace("%mesaj%", message);
                String coloredBroadcast = plugin.translateHexColorCodes(formattedMessage);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    boolean isTradeOn = plugin.getPlayerSettingsManager().isTradeEnabled(online.getUniqueId());
                    if (isTradeOn || !disableChat) {
                        online.sendMessage(coloredBroadcast);
                    }
                }
            }

            String keySuccess = plugin.getLanguageManager().contains("messages.trade-success") ? "messages.trade-success" : "messages.ticaret-basarili";
            String successMsg = plugin.getMessage(keySuccess);
            player.sendMessage(successMsg);
        });
    }

    private void sendDiscordTradeLog(Player player, String message, int slot) {
        String webhookUrl = plugin.getConfig().getString("trade-webhook-url", plugin.getConfig().getString("ticaret-webhook-url", ""));
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String tzStr = plugin.getConfig().getString("timezone", "Europe/Istanbul");
                java.time.ZoneId zoneId;
                try {
                    zoneId = java.time.ZoneId.of(tzStr);
                } catch (Exception e) {
                    zoneId = java.time.ZoneId.of("Europe/Istanbul");
                }
                java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zoneId);
                String formattedTime = now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

                // Configurations
                String rawOuterContent = plugin.getConfig().getString("trade-log-settings.outer-content", plugin.getConfig().getString("ticaret-log-settings.outer-content", "%player% is trading!"));
                String outerContent = rawOuterContent.replace("%player%", player.getName()).replace("%message%", message).replace("%mesaj%", message).replace("%time%", formattedTime).replace("{time}", formattedTime);

                String rawAuthorName = plugin.getConfig().getString("trade-log-settings.author-name", plugin.getConfig().getString("ticaret-log-settings.author-name", "%player%"));
                String authorName = rawAuthorName.replace("%player%", player.getName()).replace("%time%", formattedTime).replace("{time}", formattedTime);

                String rawAuthorIcon = plugin.getConfig().getString("trade-log-settings.author-icon", plugin.getConfig().getString("ticaret-log-settings.author-icon", "https://mc-heads.net/avatar/%player%"));
                String authorIcon = rawAuthorIcon.replace("%player%", player.getName());

                String fieldTitle = plugin.getConfig().getString("trade-log-settings.field-title", plugin.getConfig().getString("ticaret-log-settings.field-title", "**Message:**"));

                String colorHex = plugin.getConfig().getString("trade-log-settings.color", plugin.getConfig().getString("ticaret-log-settings.color", "#FF5500")).replace("#", "");
                int colorDecimal = 16733440;
                try {
                    colorDecimal = Integer.parseInt(colorHex, 16);
                } catch (NumberFormatException ignored) {}

                String footerText = plugin.getConfig().getString("trade-log-settings.footer-text", plugin.getConfig().getString("ticaret-log-settings.footer-text", "VeraxCore"))
                        .replace("%time%", formattedTime).replace("{time}", formattedTime);
                String footerIcon = plugin.getConfig().getString("trade-log-settings.footer-icon", plugin.getConfig().getString("ticaret-log-settings.footer-icon", ""));

                if (!footerText.contains(formattedTime)) {
                    footerText = footerText + " • " + formattedTime;
                }

                String description = fieldTitle + "\n```\n" + message + "\n```";

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{");
                jsonBuilder.append("\"content\": \"").append(DiscordWebhookSender.escapeJson(outerContent)).append("\",");
                jsonBuilder.append("\"embeds\": [{");
                jsonBuilder.append("\"author\": {");
                jsonBuilder.append("\"name\": \"").append(DiscordWebhookSender.escapeJson(authorName)).append("\",");
                jsonBuilder.append("\"icon_url\": \"").append(DiscordWebhookSender.escapeJson(authorIcon)).append("\"");
                jsonBuilder.append("},");
                jsonBuilder.append("\"color\": ").append(colorDecimal).append(",");
                jsonBuilder.append("\"description\": \"").append(DiscordWebhookSender.escapeJson(description)).append("\",");
                jsonBuilder.append("\"footer\": {");
                jsonBuilder.append("\"text\": \"").append(DiscordWebhookSender.escapeJson(footerText)).append("\"");
                if (footerIcon != null && !footerIcon.isEmpty()) {
                    jsonBuilder.append(",\"icon_url\": \"").append(DiscordWebhookSender.escapeJson(footerIcon)).append("\"");
                }
                jsonBuilder.append("},");
                jsonBuilder.append("\"timestamp\": \"").append(now.toOffsetDateTime().toString()).append("\"");
                jsonBuilder.append("}]");
                jsonBuilder.append("}");

                DiscordWebhookSender.sendPayload(webhookUrl, jsonBuilder.toString(), plugin.getLogger());
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[DEBUG] Discord Trade Webhook Error: " + e.getMessage());
                }
            }
        });
    }
}

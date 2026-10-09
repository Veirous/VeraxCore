package com.verax.veraxcore.systems;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.managers.DatabaseManager;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationSystem implements Listener, CommandExecutor, TabCompleter {

    public static class PendingVerification {
        private final String code;
        private final int totalSeconds;
        private int remainingSeconds;
        private final int maxAttempts;
        private int remainingAttempts;
        private BossBar bossBar;

        public PendingVerification(String code, int totalSeconds, int maxAttempts, BossBar bossBar) {
            this.code = code;
            this.totalSeconds = totalSeconds;
            this.remainingSeconds = totalSeconds;
            this.maxAttempts = maxAttempts;
            this.remainingAttempts = maxAttempts;
            this.bossBar = bossBar;
        }

        public String getCode() { return code; }
        public int getTotalSeconds() { return totalSeconds; }
        public int getRemainingSeconds() { return remainingSeconds; }
        public int decrementRemainingSeconds() { return --remainingSeconds; }
        public int getMaxAttempts() { return maxAttempts; }
        public int getRemainingAttempts() { return remainingAttempts; }
        public int decrementRemainingAttempts() { return --remainingAttempts; }
        public BossBar getBossBar() { return bossBar; }
        public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
    }

    private final VeraxCore plugin;
    private final Map<UUID, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();
    private final Map<UUID, DatabaseManager.VerifiedSessionData> verifiedSessionsCache = new ConcurrentHashMap<>();
    private BukkitTask timerTask;

    public VerificationSystem(VeraxCore plugin) {
        this.plugin = plugin;
        loadSessionsFromDatabase();
        startCountdownAndReminderTask();
    }

    public void reload() {
        loadSessionsFromDatabase();
        startCountdownAndReminderTask();
    }

    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        for (PendingVerification pending : pendingVerifications.values()) {
            if (pending.getBossBar() != null) {
                pending.getBossBar().removeAll();
            }
        }
        pendingVerifications.clear();
    }

    /**
     * Loads active, non-expired verified sessions from the database into in-memory cache.
     */
    public void loadSessionsFromDatabase() {
        if (plugin.getDatabaseManager() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            Map<UUID, DatabaseManager.VerifiedSessionData> loaded = plugin.getDatabaseManager().loadAllValidVerifiedSessions(now);
            verifiedSessionsCache.clear();
            verifiedSessionsCache.putAll(loaded);
            plugin.getDatabaseManager().cleanExpiredVerifiedSessions(now);
            plugin.logDebug("[Verification] Loaded " + loaded.size() + " active verified IP sessions from database.");
        });
    }

    public boolean isVerificationEnabled() {
        return plugin.getConfig().getBoolean("modules.verification", plugin.getConfig().getBoolean("modules.dogrulama", false));
    }

    public String getWebhookUrl() {
        String webhookUrl = plugin.getConfig().getString("verification-webhook-url", plugin.getConfig().getString("dogrulama-webhook-url", ""));
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
            return null;
        }
        return webhookUrl;
    }

    /**
     * Configured session timeout in milliseconds (default: 6 hours = 21,600,000 ms).
     */
    public long getSessionTimeoutMillis() {
        long hours = plugin.getConfig().getLong("verification-system.session-timeout-hours",
                plugin.getConfig().getLong("dogrulama-sistemi.oturum-suresi-saat", 6L));
        if (hours <= 0) hours = 6L;
        return hours * 3600L * 1000L;
    }

    public int getTimeoutSeconds() {
        return plugin.getConfig().getInt("verification-system.timeout-seconds",
                plugin.getConfig().getInt("dogrulama-sistemi.zaman-asimi-saniye", 120));
    }

    public int getMaxAttempts() {
        return plugin.getConfig().getInt("verification-system.max-attempts",
                plugin.getConfig().getInt("dogrulama-sistemi.maksimum-deneme", 3));
    }

    public static String getPlayerIp(Player player) {
        if (player == null) return "Unknown";
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    /**
     * Checks if the player has a valid, non-expired verified session for their current IP address.
     * If the session expired (> 6 hours) or the IP has changed, returns false so verification is requested.
     */
    public boolean isSessionValid(UUID uuid, String currentIp) {
        if (currentIp == null || currentIp.isEmpty() || "Unknown".equalsIgnoreCase(currentIp)) {
            return false;
        }

        DatabaseManager.VerifiedSessionData session = verifiedSessionsCache.get(uuid);
        if (session == null && plugin.getDatabaseManager() != null) {
            session = plugin.getDatabaseManager().getVerifiedSession(uuid);
            if (session != null) {
                verifiedSessionsCache.put(uuid, session);
            }
        }

        if (session == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // 1. Check if the 6-hour session window has expired
        if (now >= session.getExpiresAt()) {
            verifiedSessionsCache.remove(uuid);
            plugin.logDebug("[Verification] Session expired for UUID " + uuid + " (over " + (getSessionTimeoutMillis() / 3600000L) + " hours old).");
            return false;
        }

        // 2. Check if the IP address matches ("ama ip değişerse sorsun")
        if (!session.getIp().equalsIgnoreCase(currentIp)) {
            plugin.logDebug("[Verification] IP changed for UUID " + uuid + ": previous=" + session.getIp() + ", current=" + currentIp);
            return false;
        }

        return true;
    }

    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private String formatPlaceholders(String text, Player player, PendingVerification pending) {
        if (text == null) return "";
        String prefix = plugin.getLanguageManager() != null ? plugin.getLanguageManager().getPrefix() : "";
        return text.replace("%prefix%", prefix)
                .replace("<prefix>", prefix)
                .replace("%player%", player != null ? player.getName() : "")
                .replace("%code%", pending != null ? pending.getCode() : "")
                .replace("%kod%", pending != null ? pending.getCode() : "")
                .replace("%seconds%", pending != null ? String.valueOf(Math.max(0, pending.getRemainingSeconds())) : "0")
                .replace("%remaining_attempts%", pending != null ? String.valueOf(Math.max(0, pending.getRemainingAttempts())) : "0")
                .replace("%max_attempts%", pending != null ? String.valueOf(pending.getMaxAttempts()) : "0");
    }

    private void sendMessage(Player player, String configKey, String defaultMsg, Map<String, String> placeholders) {
        String rawMsg = plugin.getLanguageManager().getString(configKey, defaultMsg);
        String prefix = plugin.getLanguageManager().getPrefix();
        rawMsg = rawMsg.replace("%prefix%", prefix).replace("<prefix>", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rawMsg = rawMsg.replace(entry.getKey(), entry.getValue());
            }
        }
        player.sendMessage(plugin.translateHexColorCodes(rawMsg));
    }

    public void generateVerification(Player player) {
        if (!isVerificationEnabled()) return;

        // Do not demand verification if webhook is not configured, avoiding player lockouts
        if (getWebhookUrl() == null) {
            plugin.logDebug("[Verification] Skipping verification for " + player.getName() + " because Discord webhook is not configured.");
            return;
        }

        // Account list check
        List<String> accounts = plugin.getConfig().getStringList("verification-system.accounts");
        if (accounts == null || accounts.isEmpty()) {
            accounts = plugin.getConfig().getStringList("dogrulama-sistemi.hesaplar");
        }
        if (accounts != null && !accounts.isEmpty()) {
            boolean listed = false;
            for (String h : accounts) {
                if (h.equalsIgnoreCase(player.getName())) {
                    listed = true;
                    break;
                }
            }
            if (!listed) return;
        } else {
            if (!player.hasPermission("veraxcore.verification.login") && !player.hasPermission("veraxcore.dogrulama.giris") && !player.isOp()) return;
        }

        // Check if player's current IP is already verified within the 6-hour window
        String currentIp = getPlayerIp(player);
        if (isSessionValid(player.getUniqueId(), currentIp)) {
            plugin.logDebug("[Verification] Skipping verification for " + player.getName() + " (Active verified session on IP: " + currentIp + ").");
            return;
        }

        String code = generateCode();
        int timeoutSeconds = getTimeoutSeconds();
        int maxAttempts = getMaxAttempts();

        // Create BossBar if enabled
        BossBar bossBar = createBossBarForPlayer(player, code, timeoutSeconds, maxAttempts);

        PendingVerification pending = new PendingVerification(code, timeoutSeconds, maxAttempts, bossBar);
        pendingVerifications.put(player.getUniqueId(), pending);

        // Show on-screen Title / Subtitle
        showInitialTitle(player, pending);

        // Send chat message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%code%", code);
        placeholders.put("%kod%", code);
        placeholders.put("%player%", player.getName());
        placeholders.put("%seconds%", String.valueOf(timeoutSeconds));
        placeholders.put("%remaining_attempts%", String.valueOf(maxAttempts));
        placeholders.put("%max_attempts%", String.valueOf(maxAttempts));

        String primaryKey = plugin.getLanguageManager().contains("messages.verification-required")
                ? "messages.verification-required"
                : "messages.dogrulama-gerekiyor";
        sendMessage(player, primaryKey, "%prefix% &fVerification required. Please enter the code sent via Discord: &#1B4FFF%code%", placeholders);

        sendDiscordLog(player, code);
    }

    private BossBar createBossBarForPlayer(Player player, String code, int totalSeconds, int maxAttempts) {
        if (!plugin.getConfig().getBoolean("verification-system.bossbar.enabled", true)) {
            return null;
        }

        String rawTitle = plugin.getLanguageManager().getString("messages.verification-bossbar",
                plugin.getConfig().getString("verification-system.bossbar.title",
                        "&#FF0000🛡️ Verification Time: &#FFAA00%seconds%s &8| &7Attempts Left: &#FF5555%remaining_attempts%/%max_attempts%"));

        String colorStr = plugin.getConfig().getString("verification-system.bossbar.color", "RED").toUpperCase();
        BarColor barColor;
        try {
            barColor = BarColor.valueOf(colorStr);
        } catch (Exception e) {
            barColor = BarColor.RED;
        }

        String styleStr = plugin.getConfig().getString("verification-system.bossbar.style", "SOLID").toUpperCase();
        BarStyle barStyle;
        try {
            barStyle = BarStyle.valueOf(styleStr);
        } catch (Exception e) {
            barStyle = BarStyle.SOLID;
        }

        String title = rawTitle.replace("%player%", player.getName())
                .replace("%code%", code)
                .replace("%kod%", code)
                .replace("%seconds%", String.valueOf(totalSeconds))
                .replace("%remaining_attempts%", String.valueOf(maxAttempts))
                .replace("%max_attempts%", String.valueOf(maxAttempts));

        BossBar bar = Bukkit.createBossBar(plugin.translateHexColorCodes(title), barColor, barStyle);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        return bar;
    }

    private String formatBossBarTitle(Player player, PendingVerification pending) {
        String rawTitle = plugin.getLanguageManager().getString("messages.verification-bossbar",
                plugin.getConfig().getString("verification-system.bossbar.title",
                        "&#FF0000🛡️ Verification Time: &#FFAA00%seconds%s &8| &7Attempts Left: &#FF5555%remaining_attempts%/%max_attempts%"));
        return plugin.translateHexColorCodes(formatPlaceholders(rawTitle, player, pending));
    }

    private void showInitialTitle(Player player, PendingVerification pending) {
        if (!plugin.getConfig().getBoolean("verification-system.title.enabled", true)) return;

        String rawTitle = plugin.getLanguageManager().getString("messages.verification-title",
                plugin.getConfig().getString("verification-system.title.title", "&#1B4FFF&lDOĞRULAMA GEREKİYOR"));
        String rawSubtitle = plugin.getLanguageManager().getString("messages.verification-subtitle",
                plugin.getConfig().getString("verification-system.title.subtitle", "&fLütfen Discord kodunu girin: &#1BA1FF/doğrula <kod> &8(&e%remaining_attempts% Hak&8)"));

        int fadeIn = plugin.getConfig().getInt("verification-system.title.fade-in", 10);
        int stay = plugin.getConfig().getInt("verification-system.title.stay", 80);
        int fadeOut = plugin.getConfig().getInt("verification-system.title.fade-out", 20);

        String title = formatPlaceholders(rawTitle, player, pending);
        String subtitle = formatPlaceholders(rawSubtitle, player, pending);

        player.sendTitle(plugin.translateHexColorCodes(title), plugin.translateHexColorCodes(subtitle), fadeIn, stay, fadeOut);
    }

    private void showWrongCodeTitle(Player player, PendingVerification pending) {
        if (!plugin.getConfig().getBoolean("verification-system.title.enabled", true)) return;

        String rawTitle = plugin.getLanguageManager().getString("messages.verification-wrong-title",
                plugin.getConfig().getString("verification-system.title.wrong-title", "&#FF0000&lHATALI KOD!"));
        String rawSubtitle = plugin.getLanguageManager().getString("messages.verification-wrong-subtitle",
                plugin.getConfig().getString("verification-system.title.wrong-subtitle", "&cKalan deneme hakkınız: &#FFAA00%remaining_attempts%/%max_attempts%"));

        int fadeIn = plugin.getConfig().getInt("verification-system.title.fade-in", 5);
        int stay = plugin.getConfig().getInt("verification-system.title.stay", 60);
        int fadeOut = plugin.getConfig().getInt("verification-system.title.fade-out", 15);

        String title = formatPlaceholders(rawTitle, player, pending);
        String subtitle = formatPlaceholders(rawSubtitle, player, pending);

        player.sendTitle(plugin.translateHexColorCodes(title), plugin.translateHexColorCodes(subtitle), fadeIn, stay, fadeOut);
    }

    private void showSuccessTitle(Player player) {
        if (!plugin.getConfig().getBoolean("verification-system.title.enabled", true)) return;

        String rawTitle = plugin.getLanguageManager().getString("messages.verification-success-title",
                plugin.getConfig().getString("verification-system.title.success-title", "&#00FF00&lDOĞRULANDI!"));
        String rawSubtitle = plugin.getLanguageManager().getString("messages.verification-success-subtitle",
                plugin.getConfig().getString("verification-system.title.success-subtitle", "&aGiriş başarıyla onaylandı. İyi oyunlar!"));

        int fadeIn = plugin.getConfig().getInt("verification-system.title.fade-in", 10);
        int stay = plugin.getConfig().getInt("verification-system.title.stay", 60);
        int fadeOut = plugin.getConfig().getInt("verification-system.title.fade-out", 20);

        String title = formatPlaceholders(rawTitle, player, null);
        String subtitle = formatPlaceholders(rawSubtitle, player, null);

        player.sendTitle(plugin.translateHexColorCodes(title), plugin.translateHexColorCodes(subtitle), fadeIn, stay, fadeOut);
    }

    private void startCountdownAndReminderTask() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isVerificationEnabled() || pendingVerifications.isEmpty()) return;

            for (Map.Entry<UUID, PendingVerification> entry : pendingVerifications.entrySet()) {
                UUID uuid = entry.getKey();
                PendingVerification pending = entry.getValue();
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    if (pending.getBossBar() != null) {
                        pending.getBossBar().removeAll();
                    }
                    pendingVerifications.remove(uuid);
                    continue;
                }

                // Decrement countdown
                if (pending.getTotalSeconds() > 0) {
                    int remaining = pending.decrementRemainingSeconds();

                    // Update BossBar progress and title
                    if (pending.getBossBar() != null) {
                        double progress = Math.max(0.0, Math.min(1.0, (double) remaining / pending.getTotalSeconds()));
                        pending.getBossBar().setProgress(progress);
                        pending.getBossBar().setTitle(formatBossBarTitle(p, pending));
                    }

                    // Check timeout
                    if (remaining <= 0) {
                        handleTimeout(p, pending);
                        continue;
                    }
                }

                // Periodic chat reminder every 5 seconds
                if (pending.getRemainingSeconds() > 0 && pending.getRemainingSeconds() % 5 == 0) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%code%", pending.getCode());
                    placeholders.put("%kod%", pending.getCode());
                    placeholders.put("%player%", p.getName());
                    placeholders.put("%seconds%", String.valueOf(pending.getRemainingSeconds()));
                    placeholders.put("%remaining_attempts%", String.valueOf(pending.getRemainingAttempts()));
                    placeholders.put("%max_attempts%", String.valueOf(pending.getMaxAttempts()));

                    String primaryKey = plugin.getLanguageManager().contains("messages.verification-reminder")
                            ? "messages.verification-reminder"
                            : "messages.dogrulama-hatirlatma";
                    sendMessage(p, primaryKey, "%prefix% &fVerification required. Please enter the code sent via Discord: &#1B4FFF%code%", placeholders);
                }
            }
        }, 20L, 20L); // Repeat every second (20 ticks)
    }

    private void handleTimeout(Player player, PendingVerification pending) {
        pendingVerifications.remove(player.getUniqueId());
        if (pending.getBossBar() != null) {
            pending.getBossBar().removeAll();
        }

        String rawKickMsg = plugin.getLanguageManager().getString("messages.verification-timeout-kick",
                plugin.getConfig().getString("verification-system.kick-messages.timeout",
                        "&#FF0000&lDOĞRULAMA SÜRESİ DOLDU!\n&7Kodu belirtilen süre içinde girmediniz.\n&fLütfen Discord üzerinden gelen kodu zamanında giriniz."));

        String formattedKickMsg = formatPlaceholders(rawKickMsg, player, pending);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(plugin.translateHexColorCodes(formattedKickMsg));
            }
        });

        sendAlertDiscordLog(player, "⏰ Verification Timeout", "%player% failed to verify in time (" + pending.getTotalSeconds() + "s) and was disconnected.");
    }

    private void handleMaxAttemptsExceeded(Player player, PendingVerification pending) {
        pendingVerifications.remove(player.getUniqueId());
        if (pending.getBossBar() != null) {
            pending.getBossBar().removeAll();
        }

        String rawKickMsg = plugin.getLanguageManager().getString("messages.verification-max-attempts-kick",
                plugin.getConfig().getString("verification-system.kick-messages.max-attempts",
                        "&#FF0000&lÇOK FAZLA HATALI DENEME!\n&7Maksimum hatalı kod deneme limitini aştınız.\n&fGüvenlik nedeniyle bağlantınız kesildi."));

        String formattedKickMsg = formatPlaceholders(rawKickMsg, player, pending);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(plugin.translateHexColorCodes(formattedKickMsg));
            }
        });

        sendAlertDiscordLog(player, "🚨 Verification Brute-Force Alert", "%player% exceeded maximum verification attempts (" + pending.getMaxAttempts() + ") and was disconnected.");
    }

    private void sendDiscordLog(Player player, String code) {
        String webhookUrl = getWebhookUrl();
        if (webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = player.getName();
                String ip = getPlayerIp(player);

                String tzStr = plugin.getConfig().getString("timezone", "Europe/Istanbul");
                ZoneId zoneId;
                try {
                    zoneId = ZoneId.of(tzStr);
                } catch (Exception e) {
                    zoneId = ZoneId.of("Europe/Istanbul");
                }
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                String formattedTime = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

                String rawTitle = plugin.getConfig().getString("verification-system.log-settings.title", plugin.getConfig().getString("dogrulama-sistemi.log-settings.title", "🛡️ Security Verification Request"));
                String rawDesc = plugin.getConfig().getString("verification-system.log-settings.description", plugin.getConfig().getString("dogrulama-sistemi.log-settings.description", "%player% Joined the Game\nIP Address: ``%ip%``\nTo pass verification type:\n ``/verify %code%``"));
                String colorHex = plugin.getConfig().getString("verification-system.log-settings.color", plugin.getConfig().getString("dogrulama-sistemi.log-settings.color", "#FF0000")).replace("#", "");

                int colorDecimal = 0xFF0000;
                try {
                    colorDecimal = Integer.parseInt(colorHex, 16);
                } catch (NumberFormatException ignored) {}

                String rawFooter = plugin.getConfig().getString("verification-system.log-settings.footer", plugin.getConfig().getString("dogrulama-sistemi.log-settings.footer", "VeraxCore • Security System"));
                String avatarUrl = "https://minotar.net/helm/" + playerName + "/100.png";

                String title = rawTitle.replace("%player%", playerName).replace("%ip%", ip).replace("%kod%", code).replace("%code%", code).replace("%time%", formattedTime).replace("{time}", formattedTime);
                String description = rawDesc.replace("%player%", playerName).replace("%ip%", ip).replace("%kod%", code).replace("%code%", code).replace("%time%", formattedTime).replace("{time}", formattedTime);
                String footerText = rawFooter.replace("%player%", playerName).replace("%ip%", ip).replace("%kod%", code).replace("%code%", code).replace("%time%", formattedTime).replace("{time}", formattedTime);

                if (!footerText.contains(formattedTime) && !description.contains(formattedTime)) {
                    footerText = footerText + " • " + formattedTime;
                }

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{");
                jsonBuilder.append("\"embeds\": [{");
                jsonBuilder.append("\"title\": \"").append(escapeJson(title)).append("\",");
                jsonBuilder.append("\"color\": ").append(colorDecimal).append(",");
                jsonBuilder.append("\"description\": \"").append(escapeJson(description)).append("\",");
                jsonBuilder.append("\"thumbnail\": {\"url\": \"").append(escapeJson(avatarUrl)).append("\"},");
                jsonBuilder.append("\"footer\": {\"text\": \"").append(escapeJson(footerText)).append("\"},");
                jsonBuilder.append("\"timestamp\": \"").append(now.toOffsetDateTime().toString()).append("\"");
                jsonBuilder.append("}]");
                jsonBuilder.append("}");

                DiscordWebhookSender.sendPayload(webhookUrl, jsonBuilder.toString(), plugin.getLogger());
            } catch (Exception e) {
                plugin.getLogger().warning("[SECURITY] Discord Webhook error: " + e.getMessage());
            }
        });
    }

    private void sendSuccessDiscordLog(Player player) {
        String webhookUrl = getWebhookUrl();
        if (webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = player.getName();
                String ip = getPlayerIp(player);
                long timeoutHours = getSessionTimeoutMillis() / 3600000L;

                String tzStr = plugin.getConfig().getString("timezone", "Europe/Istanbul");
                ZoneId zoneId;
                try {
                    zoneId = ZoneId.of(tzStr);
                } catch (Exception e) {
                    zoneId = ZoneId.of("Europe/Istanbul");
                }
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                String formattedTime = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

                String rawTitle = plugin.getConfig().getString("verification-system.success-log-settings.title", plugin.getConfig().getString("dogrulama-sistemi.success-log-settings.title", "✅ Security Verification Successful"));
                String rawDesc = plugin.getConfig().getString("verification-system.success-log-settings.description", plugin.getConfig().getString("dogrulama-sistemi.success-log-settings.description", "%player% passed verification by entering the correct code!\nIP Address: ``%ip%``\nSession Duration: ``" + timeoutHours + " hours``"));
                String colorHex = plugin.getConfig().getString("verification-system.success-log-settings.color", plugin.getConfig().getString("dogrulama-sistemi.success-log-settings.color", "#00FF00")).replace("#", "");

                int colorDecimal = 0x00FF00;
                try {
                    colorDecimal = Integer.parseInt(colorHex, 16);
                } catch (NumberFormatException ignored) {}

                String rawFooter = plugin.getConfig().getString("verification-system.success-log-settings.footer", plugin.getConfig().getString("dogrulama-sistemi.success-log-settings.footer", "VeraxCore • Security System"));
                String avatarUrl = "https://minotar.net/helm/" + playerName + "/100.png";

                String title = rawTitle.replace("%player%", playerName).replace("%ip%", ip).replace("%time%", formattedTime).replace("{time}", formattedTime);
                String description = rawDesc.replace("%player%", playerName).replace("%ip%", ip).replace("%time%", formattedTime).replace("{time}", formattedTime)
                        .replace("%session_hours%", String.valueOf(timeoutHours));
                String footerText = rawFooter.replace("%player%", playerName).replace("%ip%", ip).replace("%time%", formattedTime).replace("{time}", formattedTime);

                if (!footerText.contains(formattedTime) && !description.contains(formattedTime)) {
                    footerText = footerText + " • " + formattedTime;
                }

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{");
                jsonBuilder.append("\"embeds\": [{");
                jsonBuilder.append("\"title\": \"").append(escapeJson(title)).append("\",");
                jsonBuilder.append("\"color\": ").append(colorDecimal).append(",");
                jsonBuilder.append("\"description\": \"").append(escapeJson(description)).append("\",");
                jsonBuilder.append("\"thumbnail\": {\"url\": \"").append(escapeJson(avatarUrl)).append("\"},");
                jsonBuilder.append("\"footer\": {\"text\": \"").append(escapeJson(footerText)).append("\"},");
                jsonBuilder.append("\"timestamp\": \"").append(now.toOffsetDateTime().toString()).append("\"");
                jsonBuilder.append("}]");
                jsonBuilder.append("}");

                DiscordWebhookSender.sendPayload(webhookUrl, jsonBuilder.toString(), plugin.getLogger());
            } catch (Exception e) {
                plugin.getLogger().warning("[SECURITY] Successful verification Discord Webhook error: " + e.getMessage());
            }
        });
    }

    private void sendAlertDiscordLog(Player player, String alertTitle, String alertDescription) {
        String webhookUrl = getWebhookUrl();
        if (webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = player.getName();
                String ip = getPlayerIp(player);

                ZoneId zoneId = plugin.getValidZoneId();
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                String formattedTime = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

                String description = alertDescription.replace("%player%", playerName).replace("%ip%", ip) + "\n**IP Address:** ``" + ip + "``";
                String footerText = "VeraxCore • Security Alert • " + formattedTime;
                String avatarUrl = "https://minotar.net/helm/" + playerName + "/100.png";

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{");
                jsonBuilder.append("\"embeds\": [{");
                jsonBuilder.append("\"title\": \"").append(escapeJson(alertTitle)).append("\",");
                jsonBuilder.append("\"color\": 16711680,"); // Red
                jsonBuilder.append("\"description\": \"").append(escapeJson(description)).append("\",");
                jsonBuilder.append("\"thumbnail\": {\"url\": \"").append(escapeJson(avatarUrl)).append("\"},");
                jsonBuilder.append("\"footer\": {\"text\": \"").append(escapeJson(footerText)).append("\"},");
                jsonBuilder.append("\"timestamp\": \"").append(now.toOffsetDateTime().toString()).append("\"");
                jsonBuilder.append("}]");
                jsonBuilder.append("}");

                DiscordWebhookSender.sendPayload(webhookUrl, jsonBuilder.toString(), plugin.getLogger());
            } catch (Exception e) {
                plugin.getLogger().warning("[SECURITY] Discord Alert Webhook error: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    // --- EVENT LISTENERS (LOCK UNVERIFIED PLAYER) ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isVerificationEnabled()) return;
        Player player = event.getPlayer();
        generateVerification(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingVerification pending = pendingVerifications.remove(uuid);
        if (pending != null && pending.getBossBar() != null) {
            pending.getBossBar().removeAll();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (pendingVerifications.containsKey(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (pendingVerifications.isEmpty()) return;
        Player player = event.getPlayer();
        PendingVerification pending = pendingVerifications.get(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            String code = pending.getCode();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%code%", code != null ? code : "");
            placeholders.put("%kod%", code != null ? code : "");
            placeholders.put("%player%", player.getName());
            placeholders.put("%seconds%", String.valueOf(pending.getRemainingSeconds()));
            placeholders.put("%remaining_attempts%", String.valueOf(pending.getRemainingAttempts()));
            placeholders.put("%max_attempts%", String.valueOf(pending.getMaxAttempts()));

            String primaryKey = plugin.getLanguageManager().contains("messages.verification-required")
                    ? "messages.verification-required"
                    : "messages.dogrulama-gerekiyor";
            sendMessage(player, primaryKey, "%prefix% &fVerification required. Please enter the code sent via Discord: &#1B4FFF%code%", placeholders);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (pendingVerifications.isEmpty()) return;
        Player player = event.getPlayer();
        PendingVerification pending = pendingVerifications.get(player.getUniqueId());
        if (pending != null) {
            String msg = event.getMessage().toLowerCase().trim();
            if (!msg.startsWith("/doğrula") && !msg.startsWith("/dogrula") && !msg.startsWith("/verify")
                    && !msg.startsWith("/veraxcore:doğrula") && !msg.startsWith("/veraxcore:dogrula") && !msg.startsWith("/veraxcore:verify")) {
                event.setCancelled(true);
                String code = pending.getCode();
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%code%", code != null ? code : "");
                placeholders.put("%kod%", code != null ? code : "");
                placeholders.put("%player%", player.getName());
                placeholders.put("%seconds%", String.valueOf(pending.getRemainingSeconds()));
                placeholders.put("%remaining_attempts%", String.valueOf(pending.getRemainingAttempts()));
                placeholders.put("%max_attempts%", String.valueOf(pending.getMaxAttempts()));

                String primaryKey = plugin.getLanguageManager().contains("messages.verification-required")
                        ? "messages.verification-required"
                        : "messages.dogrulama-gerekiyor";
                sendMessage(player, primaryKey, "%prefix% &fVerification required. Please enter the code sent via Discord: &#1B4FFF%code%", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (pendingVerifications.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (pendingVerifications.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (event.getEntity() instanceof Player p && pendingVerifications.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (event.getDamager() instanceof Player p && pendingVerifications.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (event.getWhoClicked() instanceof Player p && pendingVerifications.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (pendingVerifications.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (event.getEntity() instanceof Player p && pendingVerifications.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemInteract(PlayerInteractEvent event) {
        if (pendingVerifications.isEmpty()) return;
        if (pendingVerifications.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    // --- COMMAND EXECUTOR (/verify or /doğrula <code>) ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        PendingVerification pending = pendingVerifications.get(uuid);
        if (pending == null) {
            String key = plugin.getLanguageManager().contains("messages.verification-not-needed") ? "messages.verification-not-needed" : "messages.dogrulama-gerek-yok";
            sendMessage(player, key, "%prefix% &aYou do not have a pending verification or you are already verified.", null);
            return true;
        }

        if (args.length == 0) {
            String key = plugin.getLanguageManager().contains("messages.verification-usage") ? "messages.verification-usage" : "messages.dogrulama-kullanim";
            sendMessage(player, key, "%prefix% &cUsage: &#1BA1FF/" + label.toLowerCase() + " <code>", null);
            return true;
        }

        String inputCode = args[0].trim();
        String expectedCode = pending.getCode();

        if (expectedCode.equals(inputCode)) {
            // VERIFICATION SUCCESSFUL
            pendingVerifications.remove(uuid);
            if (pending.getBossBar() != null) {
                pending.getBossBar().removeAll();
            }

            // Save verified session for this player's current IP for the configured timeout (default 6 hours)
            String currentIp = getPlayerIp(player);
            long timeoutMillis = getSessionTimeoutMillis();
            long timeoutHours = timeoutMillis / 3600000L;

            if (!"Unknown".equalsIgnoreCase(currentIp)) {
                long now = System.currentTimeMillis();
                long expiresAt = now + timeoutMillis;

                DatabaseManager.VerifiedSessionData sessionData = new DatabaseManager.VerifiedSessionData(currentIp, now, expiresAt);
                verifiedSessionsCache.put(uuid, sessionData);

                if (plugin.getDatabaseManager() != null) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDatabaseManager().saveVerifiedSession(uuid, currentIp, now, expiresAt);
                    });
                }
                plugin.logDebug("[Verification] IP session saved for " + player.getName() + " (" + currentIp + ") valid for " + timeoutHours + " hours.");
            }

            // Show success title
            showSuccessTitle(player);

            // Play success sound
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } catch (Exception ignored) {}

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%hours%", String.valueOf(timeoutHours));
            placeholders.put("%session_hours%", String.valueOf(timeoutHours));
            placeholders.put("%ip%", currentIp);
            placeholders.put("%player%", player.getName());

            String key = plugin.getLanguageManager().contains("messages.verification-success") ? "messages.verification-success" : "messages.dogrulama-basarili";
            sendMessage(player, key, "%prefix% &fVerification &aSuccessfully &fCompleted!", placeholders);

            sendSuccessDiscordLog(player);
        } else {
            // VERIFICATION FAILED
            int remainingAttempts = pending.decrementRemainingAttempts();

            // Check if max attempts exceeded
            if (remainingAttempts <= 0 && pending.getMaxAttempts() > 0) {
                handleMaxAttemptsExceeded(player, pending);
                return true;
            }

            // Play fail sound
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } catch (Exception ignored) {}

            // Update BossBar title with new attempt count immediately
            if (pending.getBossBar() != null) {
                pending.getBossBar().setTitle(formatBossBarTitle(player, pending));
            }

            // Show wrong code title
            showWrongCodeTitle(player, pending);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%remaining_attempts%", String.valueOf(remainingAttempts));
            placeholders.put("%max_attempts%", String.valueOf(pending.getMaxAttempts()));
            placeholders.put("%seconds%", String.valueOf(pending.getRemainingSeconds()));
            placeholders.put("%player%", player.getName());

            String key = plugin.getLanguageManager().contains("messages.verification-failed") ? "messages.verification-failed" : "messages.dogrulama-hatali";
            sendMessage(player, key, "%prefix% &cInvalid Code! Remaining attempts: &#FFAA00" + remainingAttempts, placeholders);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

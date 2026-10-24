package com.verax.veraxcore.listeners;

import com.verax.veraxcore.VeraxCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import com.verax.veraxcore.utils.AdventureUtil;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatControlListener implements Listener {

    private final VeraxCore plugin;
    private boolean isChatOpen = true;
    private final Map<UUID, Long> chatCooldowns = new ConcurrentHashMap<>();
    private final List<WordActionRule> cachedWordActions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastChatProcessTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastChatContent = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastChatCancelResult = new ConcurrentHashMap<>();

    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public static class WordActionRule {
        private final String word;
        private final String wordTr;
        private final Pattern patternRoot;
        private final Pattern patternTr;
        private final boolean cancelChat;
        private final List<String> consoleCommands;
        private final List<String> playerCommands;
        private final String message;

        public WordActionRule(String word, boolean cancelChat, List<String> consoleCommands, List<String> playerCommands, String message) {
            String trimmed = word != null ? word.trim() : "";
            this.word = trimmed.toLowerCase(Locale.ROOT);
            this.wordTr = trimmed.toLowerCase(new Locale("tr", "TR"));
            this.patternRoot = buildWordPattern(this.word);
            this.patternTr = buildWordPattern(this.wordTr);
            this.cancelChat = cancelChat;
            this.consoleCommands = consoleCommands != null ? consoleCommands : new ArrayList<>();
            this.playerCommands = playerCommands != null ? playerCommands : new ArrayList<>();
            this.message = message;
        }

        private static Pattern buildWordPattern(String target) {
            if (target == null || target.isEmpty()) {
                return Pattern.compile("(?!)");
            }
            boolean startBoundary = Character.isLetterOrDigit(target.codePointAt(0));
            boolean endBoundary = Character.isLetterOrDigit(target.codePointBefore(target.length()));
            String prefix = startBoundary ? "(?<![\\p{L}\\p{N}])" : "";
            String suffix = endBoundary ? "(?![\\p{L}\\p{N}])" : "";
            return Pattern.compile(prefix + Pattern.quote(target) + suffix, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        public boolean matches(String plainMessageRoot, String plainMessageTr) {
            return patternRoot.matcher(plainMessageRoot).find() || patternTr.matcher(plainMessageTr).find();
        }

        public String getWord() { return word; }
        public boolean isCancelChat() { return cancelChat; }
        public List<String> getConsoleCommands() { return consoleCommands; }
        public List<String> getPlayerCommands() { return playerCommands; }
        public String getMessage() { return message; }
    }

    public ChatControlListener(VeraxCore plugin) {
        this.plugin = plugin;
        reloadWordActions();
    }

    public void reloadWordActions() {
        cachedWordActions.clear();
        ConfigurationSection wordSection = plugin.getConfig().getConfigurationSection("word-actions");
        if (wordSection != null) {
            for (String key : wordSection.getKeys(false)) {
                if (key.equalsIgnoreCase("enabled")) continue;
                if (wordSection.isConfigurationSection(key) && !wordSection.getBoolean(key + ".enabled", true)) continue;

                String targetWord = wordSection.getString(key + ".word", "");
                if (targetWord != null && !targetWord.trim().isEmpty()) {
                    boolean cancelChat = wordSection.getBoolean(key + ".cancel-chat", false);
                    List<String> consoleCmds = wordSection.getStringList(key + ".actions.console-commands");
                    List<String> playerCmds = wordSection.getStringList(key + ".actions.player-commands");
                    String msg = wordSection.getString(key + ".actions.message", null);
                    cachedWordActions.add(new WordActionRule(targetWord, cancelChat, consoleCmds, playerCmds, msg));
                }
            }
        }
    }

    public boolean isChatOpen() { return isChatOpen; }
    public void setChatOpen(boolean chatOpen) { isChatOpen = chatOpen; }

    public boolean isChatMuted() { 
        return !isChatOpen; 
    }

    public void setChatMuted(boolean chatMuted) { 
        this.isChatOpen = !chatMuted; 
    }

    public void sendDiscordLog(String executorName, String action) {
        String webhookUrl = plugin.getConfig().getString("chat-log-webhook-url", "");
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
            return;
        }

        String tzStr = plugin.getConfig().getString("timezone", "Europe/Istanbul");
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tzStr);
        } catch (Exception e) {
            zoneId = ZoneId.of("Europe/Istanbul");
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String formattedTime = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

        String rawTitle = plugin.getConfig().getString("chat-log-settings.title", "🛡️ Chat Management Log");
        String rawDescription = plugin.getConfig().getString("chat-log-settings.description", "**Staff:** {player}\n**Action:** {action}")
                .replace("{player}", executorName)
                .replace("{action}", action)
                .replace("{time}", formattedTime)
                .replace("%time%", formattedTime);
        String rawFooter = plugin.getConfig().getString("chat-log-settings.footer", "VeraxCore • Chat Log System")
                .replace("{time}", formattedTime)
                .replace("%time%", formattedTime);

        if (!rawFooter.contains(formattedTime) && !rawDescription.contains(formattedTime)) {
            rawFooter = rawFooter + " • " + formattedTime;
        }
        
        String colorHex = plugin.getConfig().getString("chat-log-settings.color", "#1B4FFF").replace("#", "");
        int colorInt = 1789951;
        try {
            colorInt = Integer.parseInt(colorHex, 16);
        } catch (NumberFormatException ignored) {}

        final int finalColor = colorInt;
        final String finalWebhookUrl = webhookUrl;
        final String finalTitle = rawTitle;
        final String finalDescription = rawDescription;
        final String finalFooter = rawFooter;
        final String isoTimestamp = now.toOffsetDateTime().toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String jsonTitle = DiscordWebhookSender.escapeJson(finalTitle);
                String jsonDesc = DiscordWebhookSender.escapeJson(finalDescription);
                String jsonFooter = DiscordWebhookSender.escapeJson(finalFooter);

                String jsonPayload = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"" + jsonTitle + "\","
                        + "\"description\": \"" + jsonDesc + "\","
                        + "\"color\": " + finalColor + ","
                        + "\"footer\": {\"text\": \"" + jsonFooter + "\"},"
                        + "\"timestamp\": \"" + isoTimestamp + "\""
                        + "}]"
                        + "}";

                DiscordWebhookSender.sendPayload(finalWebhookUrl, jsonPayload, plugin.getLogger());
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending chat Webhook log: " + e.getMessage());
            }
        });
    }

    public Component parseText(String text, String senderName) {
        if (text == null || text.isEmpty()) return Component.empty();
        String rawPrefix = plugin.getLanguageManager().getPrefix();
        String processedText = text.replace("<prefix>", rawPrefix).replace("%prefix%", rawPrefix);
        if (senderName != null) {
            processedText = processedText.replace("{player}", senderName).replace("<by>", senderName).replace("%player%", senderName);
        }
        return legacySerializer.deserialize(processedText);
    }

    public Component parseText(String text) {
        return parseText(text, null);
    }

    public Component getConfigMessage(String path) {
        return parseText(plugin.getLanguageManager().getRawMessage(path));
    }

    public Component getConfigMessage(String path, String senderName) {
        return parseText(plugin.getLanguageManager().getRawMessage(path), senderName);
    }

    /**
     * Modern Paper 1.20.4+ Adventure AsyncChatEvent handler.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onModernChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        String rawMsg = AdventureUtil.toPlainText(event.message());
        if (shouldCancelChat(player, rawMsg)) {
            event.setCancelled(true);
        }
    }

    /**
     * Legacy / MockBukkit chat event fallback.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (shouldCancelChat(player, event.getMessage())) {
            event.setCancelled(true);
        }
    }

    /**
     * Core chat filtering logic (Word Actions, Chat Mute, Chat Cooldown/Spam).
     * Thread-safe and deduplicated so dual-firing events within 50ms won't double-trigger.
     *
     * @return true if the chat event should be cancelled, false otherwise.
     */
    public boolean shouldCancelChat(Player player, String rawMsg) {
        if (player == null || rawMsg == null) return false;

        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Prevent double evaluation if both modern and legacy events fire for the same chat packet
        Long lastTime = lastChatProcessTime.get(playerUUID);
        String lastMsg = lastChatContent.get(playerUUID);
        if (lastTime != null && (now - lastTime) < 500L && java.util.Objects.equals(lastMsg, rawMsg)) {
            return Boolean.TRUE.equals(lastChatCancelResult.get(playerUUID));
        }

        String plainMessageRoot = rawMsg.toLowerCase(Locale.ROOT);
        String plainMessageTr = rawMsg.toLowerCase(new Locale("tr", "TR"));

        // 1. Word Actions Check (ultra-fast check from RAM cache)
        boolean wordActionsEnabled = plugin.getConfig().getBoolean("modules.word-actions", plugin.getConfig().getBoolean("modules.kelime-eylemleri", true))
                && plugin.getConfig().getBoolean("word-actions.enabled", true);
        if (wordActionsEnabled && !cachedWordActions.isEmpty()) {
            for (WordActionRule rule : cachedWordActions) {
                if (rule.matches(plainMessageRoot, plainMessageTr)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String cmd : rule.getConsoleCommands()) {
                            if (cmd != null && !cmd.trim().isEmpty()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()).replace("{player}", player.getName()));
                            }
                        }

                        for (String cmd : rule.getPlayerCommands()) {
                            if (cmd != null && !cmd.trim().isEmpty()) {
                                player.performCommand(cmd.replace("%player%", player.getName()).replace("{player}", player.getName()));
                            }
                        }

                        if (rule.getMessage() != null && !rule.getMessage().isEmpty()) {
                            player.sendMessage(parseText(rule.getMessage(), player.getName()));
                        }
                    });

                    if (rule.isCancelChat()) {
                        recordResult(playerUUID, now, rawMsg, true);
                        return true;
                    }
                }
            }
        }

        // 2. Muted Chat Check
        if (!isChatOpen && !player.hasPermission("veraxcore.chat.talk") && !player.hasPermission("veraxcore.sohbet.yaz") && !player.isOp()) {
            player.sendMessage(getConfigMessage("messages.chat-closed-error", player.getName()));
            recordResult(playerUUID, now, rawMsg, true);
            return true;
        }

        // 3. Chat Spam / Cooldown Protection
        if (!player.hasPermission("veraxcore.chat.spambypass") && !player.hasPermission("veraxcore.chat.bypass") 
                && !player.hasPermission("veraxcore.sohbet.spambypass") && !player.hasPermission("veraxcore.sohbet.bypass") && !player.isOp()) {
            long cooldownConfig = plugin.getConfig().getLong("settings.cooldown-time", plugin.getConfig().getLong("settings.chat-cooldown", 2));
            long cooldownTime = cooldownConfig > 50 ? cooldownConfig : cooldownConfig * 1000L;

            if (chatCooldowns.containsKey(playerUUID)) {
                long lastChatTime = chatCooldowns.get(playerUUID);
                long timePassed = now - lastChatTime;

                if (timePassed < cooldownTime) {
                    double remainingSeconds = (cooldownTime - timePassed) / 1000.0;
                    String formattedRemaining = String.format(Locale.US, "%.1f", remainingSeconds);
                    
                    String spamMsg = plugin.getLanguageManager().getString("messages.spam-warning", "%prefix% &fPlease wait &#1B4FFF%remaining% &fseconds before chatting again.")
                            .replace("%remaining%", formattedRemaining)
                            .replace("<remaining>", formattedRemaining);
                    
                    player.sendMessage(parseText(spamMsg, player.getName()));
                    recordResult(playerUUID, now, rawMsg, true);
                    return true;
                }
            }
            chatCooldowns.put(playerUUID, now);
        }

        recordResult(playerUUID, now, rawMsg, false);
        return false;
    }

    private void recordResult(UUID uuid, long timestamp, String rawMsg, boolean cancelled) {
        lastChatProcessTime.put(uuid, timestamp);
        lastChatContent.put(uuid, rawMsg);
        lastChatCancelResult.put(uuid, cancelled);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chatCooldowns.remove(uuid);
        lastChatProcessTime.remove(uuid);
        lastChatContent.remove(uuid);
        lastChatCancelResult.remove(uuid);
    }
}

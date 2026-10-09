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

public class ChatControlListener implements Listener {

    private final VeraxCore plugin;
    private boolean isChatOpen = true;
    private final Map<UUID, Long> chatCooldowns = new ConcurrentHashMap<>();
    private final List<WordActionRule> cachedWordActions = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public static class WordActionRule {
        private final String word;
        private final boolean cancelChat;
        private final List<String> consoleCommands;
        private final List<String> playerCommands;
        private final String message;

        public WordActionRule(String word, boolean cancelChat, List<String> consoleCommands, List<String> playerCommands, String message) {
            this.word = word.toLowerCase(Locale.ROOT);
            this.cancelChat = cancelChat;
            this.consoleCommands = consoleCommands != null ? consoleCommands : new ArrayList<>();
            this.playerCommands = playerCommands != null ? playerCommands : new ArrayList<>();
            this.message = message;
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

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        String rawMsg = event.getMessage();
        String plainMessageRoot = rawMsg.toLowerCase(Locale.ROOT);
        String plainMessageTr = rawMsg.toLowerCase(new Locale("tr", "TR"));

        // 1. Word Actions Check (ultra-fast check from RAM cache)
        boolean wordActionsEnabled = plugin.getConfig().getBoolean("modules.word-actions", plugin.getConfig().getBoolean("modules.kelime-eylemleri", true))
                && plugin.getConfig().getBoolean("word-actions.enabled", true);
        if (wordActionsEnabled && !cachedWordActions.isEmpty()) {
            for (WordActionRule rule : cachedWordActions) {
                String target = rule.getWord();
                if (plainMessageRoot.contains(target) || plainMessageTr.contains(target)) {
                    if (rule.isCancelChat()) {
                        event.setCancelled(true);
                    }

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

                    if (event.isCancelled()) return;
                }
            }
        }

        // 2. Muted Chat Check
        if (!isChatOpen && !player.hasPermission("veraxcore.chat.talk") && !player.hasPermission("veraxcore.sohbet.yaz") && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(getConfigMessage("messages.chat-closed-error", player.getName()));
            return;
        }

        // 3. Chat Spam / Cooldown Protection
        if (!player.hasPermission("veraxcore.chat.spambypass") && !player.hasPermission("veraxcore.chat.bypass") 
                && !player.hasPermission("veraxcore.sohbet.spambypass") && !player.hasPermission("veraxcore.sohbet.bypass") && !player.isOp()) {
            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long cooldownConfig = plugin.getConfig().getLong("settings.cooldown-time", plugin.getConfig().getLong("settings.chat-cooldown", 2));
            long cooldownTime = cooldownConfig > 50 ? cooldownConfig : cooldownConfig * 1000L;

            if (chatCooldowns.containsKey(playerUUID)) {
                long lastChatTime = chatCooldowns.get(playerUUID);
                long timePassed = currentTime - lastChatTime;

                if (timePassed < cooldownTime) {
                    event.setCancelled(true);
                    double remainingSeconds = (cooldownTime - timePassed) / 1000.0;
                    String formattedRemaining = String.format(Locale.US, "%.1f", remainingSeconds);
                    
                    String spamMsg = plugin.getLanguageManager().getString("messages.spam-warning", "%prefix% &fPlease wait &#1B4FFF%remaining% &fseconds before chatting again.")
                            .replace("%remaining%", formattedRemaining)
                            .replace("<remaining>", formattedRemaining);
                    
                    player.sendMessage(parseText(spamMsg, player.getName()));
                    return;
                }
            }
            chatCooldowns.put(playerUUID, currentTime);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        chatCooldowns.remove(event.getPlayer().getUniqueId());
    }
}

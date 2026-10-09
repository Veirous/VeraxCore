package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.listeners.ActivityListener;
import com.verax.veraxcore.utils.AdventureUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaffChatCommand implements CommandExecutor, TabCompleter, Listener {

    private final VeraxCore plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Set<UUID> toggledStaffChat = ConcurrentHashMap.newKeySet();

    public StaffChatCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isStaffChatToggled(UUID uuid) {
        return uuid != null && toggledStaffChat.contains(uuid);
    }

    public static void setStaffChatToggled(UUID uuid, boolean toggled) {
        if (uuid == null) return;
        if (toggled) {
            toggledStaffChat.add(uuid);
        } else {
            toggledStaffChat.remove(uuid);
        }
    }

    public static boolean toggleStaffChat(UUID uuid) {
        if (uuid == null) return false;
        if (toggledStaffChat.contains(uuid)) {
            toggledStaffChat.remove(uuid);
            return false;
        } else {
            toggledStaffChat.add(uuid);
            return true;
        }
    }

    public static String colorize(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : color.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String renkledir(String mesaj) {
        return colorize(mesaj);
    }

    public static boolean hasStaffRank(VeraxCore plugin, Player player) {
        if (player == null || !player.isOnline()) return false;
        // If OP or has staff chat permission, allow directly
        if (player.isOp() || player.hasPermission("veraxcore.staffchat") || player.hasPermission("veraxcore.ys")) {
            return true;
        }

        return ActivityListener.hasStaffRankStatic(plugin, player);
    }

    public static boolean yetkiliRankinaSahipMi(VeraxCore plugin, Player player) {
        return hasStaffRank(plugin, player);
    }

    public static void sendStaffMessage(VeraxCore plugin, Player senderPlayer, String message) {
        FileConfiguration config = plugin.getConfig();

        // Module check
        if (!config.getBoolean("modules.staff-chat", config.getBoolean("modules.yetkili-sohbet", true))) {
            senderPlayer.sendMessage(plugin.getMessage("messages.module-disabled"));
            return;
        }

        if (!hasStaffRank(plugin, senderPlayer)) {
            senderPlayer.sendMessage(plugin.getMessage("messages.no-permission"));
            return;
        }

        if (message == null || message.trim().isEmpty()) {
            senderPlayer.sendMessage(colorize("&cUsage: &8[&6/sc <message>&8]"));
            return;
        }

        // Read format from language file or configuration
        String defaultFormat = "&#1B4FFFS&#1B58FFt&#1B61FFa&#1B6AFFf&#1B73FFf &#1B86FFC&#1B8FFFh&#1B98FFa&#1BA1FFt &8: &#1BA1FF{player} &8» &f{message}";
        String configFormat = plugin.getLanguageManager().getString("staff-chat.format", plugin.getLanguageManager().getString("yetkili-sohbet.format", defaultFormat));
        
        String format = configFormat
                .replace("{player}", senderPlayer.getName())
                .replace("{message}", message.trim())
                .replace("{mesaj}", message.trim());

        String formattedMessage = plugin.translateHexColorCodes(format);

        // Read sound settings from config
        String soundName = config.getString("staff-chat.sound", config.getString("yetkili-sohbet.ses", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        float volume = (float) config.getDouble("staff-chat.volume", config.getDouble("yetkili-sohbet.volume", 1.0));
        float pitch = (float) config.getDouble("staff-chat.pitch", config.getDouble("yetkili-sohbet.pitch", 1.2));

        Sound sound = null;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Staff chat sound '" + soundName + "' in config not found! Using default sound.");
            try {
                sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            } catch (Exception ignored) {}
        }

        // Broadcast to all online staff members
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (hasStaffRank(plugin, target)) {
                AdventureUtil.sendMessage(target, formattedMessage);
                if (sound != null) {
                    target.playSound(target.getLocation(), sound, volume, pitch);
                }
            }
        }

        // Configurable console log format per language
        if (config.getBoolean("staff-chat.log-to-console", true)) {
            String defaultLogFormat = "[StaffChat] {player}: {message}";
            String logFormat = plugin.getLanguageManager().getString("staff-chat.console-log-format", defaultLogFormat);
            String consoleLog = logFormat
                    .replace("{player}", senderPlayer.getName())
                    .replace("%player%", senderPlayer.getName())
                    .replace("{message}", message.trim())
                    .replace("%message%", message.trim())
                    .replace("{mesaj}", message.trim())
                    .replace("%mesaj%", message.trim());
            plugin.getLogger().info(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', consoleLog)));
        }
    }

    public static void mesajGonder(VeraxCore plugin, Player gonderen, String mesaj) {
        sendStaffMessage(plugin, gonderen, mesaj);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player senderPlayer)) {
            sender.sendMessage(plugin.getMessage("messages.only-players"));
            return true;
        }

        // Module check
        if (!plugin.getConfig().getBoolean("modules.staff-chat", plugin.getConfig().getBoolean("modules.yetkili-sohbet", true))) {
            senderPlayer.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (!hasStaffRank(plugin, senderPlayer)) {
            senderPlayer.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        // Toggle mode when used without arguments
        if (args.length == 0) {
            UUID uuid = senderPlayer.getUniqueId();
            boolean newState = toggleStaffChat(uuid);
            if (newState) {
                String key = plugin.getLanguageManager().contains("staff-chat.toggle-on") ? "staff-chat.toggle-on" : "yetkili-sohbet.toggle-on";
                String msg = plugin.getMessage(key);
                if (msg == null || msg.isEmpty()) {
                    msg = colorize("&aStaff chat mode enabled. Your messages will now be sent to staff chat.");
                }
                senderPlayer.sendMessage(msg);
            } else {
                String key = plugin.getLanguageManager().contains("staff-chat.toggle-off") ? "staff-chat.toggle-off" : "yetkili-sohbet.toggle-off";
                String msg = plugin.getMessage(key);
                if (msg == null || msg.isEmpty()) {
                    msg = colorize("&cStaff chat mode disabled. You are now speaking in global chat.");
                }
                senderPlayer.sendMessage(msg);
            }
            return true;
        }

        // One-shot message when arguments are provided
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }

        sendStaffMessage(plugin, senderPlayer, sb.toString());
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isStaffChatToggled(player.getUniqueId())) {
            if (!hasStaffRank(plugin, player)) {
                toggledStaffChat.remove(player.getUniqueId());
                return;
            }

            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> {
                sendStaffMessage(plugin, player, message);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        toggledStaffChat.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.listeners.ChatControlListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChatCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final ChatControlListener chatListener;

    public ChatCommand(VeraxCore plugin, ChatControlListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Module check (modules.chat / modules.sohbet)
        if (!plugin.getConfig().getBoolean("modules.chat", plugin.getConfig().getBoolean("modules.sohbet", true))) {
            sender.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUsage: §f/" + label.toLowerCase() + " <open|close|clear>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String senderName = sender.getName();

        switch (subCommand) {
            case "close":
            case "kapat":
            case "lock":
            case "kilitle":
                if (!sender.hasPermission("veraxcore.chat.lock") && !sender.hasPermission("veraxcore.chat.close") && !sender.hasPermission("veraxcore.sohbet.kapat")) {
                    sender.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                if (chatListener.isChatMuted()) {
                    sender.sendMessage(plugin.getMessage("messages.chat-already-closed"));
                } else {
                    chatListener.setChatMuted(true);
                    Bukkit.broadcastMessage(plugin.getMessage("messages.chat-closed-broadcast", senderName));
                    chatListener.sendDiscordLog(senderName, "Chat locked (closed).");
                }
                break;

            case "open":
            case "aç":
            case "ac":
                if (!sender.hasPermission("veraxcore.chat.open") && !sender.hasPermission("veraxcore.sohbet.aç") && !sender.hasPermission("veraxcore.sohbet.ac")) {
                    sender.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                if (!chatListener.isChatMuted()) {
                    sender.sendMessage(plugin.getMessage("messages.chat-already-open"));
                } else {
                    chatListener.setChatMuted(false);
                    Bukkit.broadcastMessage(plugin.getMessage("messages.chat-opened-broadcast", senderName));
                    chatListener.sendDiscordLog(senderName, "Chat re-opened.");
                }
                break;

            case "clear":
            case "temizle":
            case "sil":
                if (!sender.hasPermission("veraxcore.chat.clear") && !sender.hasPermission("veraxcore.sohbet.temizle")) {
                    sender.sendMessage(plugin.getMessage("messages.no-permission"));
                    return true;
                }
                String clearBlock = "\n".repeat(100);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(clearBlock);
                }
                Bukkit.broadcastMessage(plugin.getMessage("messages.chat-cleared-broadcast", senderName));
                chatListener.sendDiscordLog(senderName, "Chat cleared.");
                break;

            default:
                sender.sendMessage("§eUsage: §f/" + label.toLowerCase() + " <open|close|clear>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> allowed = new ArrayList<>();

            if (sender.isOp() || sender.hasPermission("veraxcore.admin")) {
                allowed.addAll(Arrays.asList("open", "close", "clear"));
            } else {
                if (sender.hasPermission("veraxcore.chat.open") || sender.hasPermission("veraxcore.sohbet.aç") || sender.hasPermission("veraxcore.sohbet.ac")) {
                    allowed.add("open");
                }
                if (sender.hasPermission("veraxcore.chat.lock") || sender.hasPermission("veraxcore.chat.close") || sender.hasPermission("veraxcore.sohbet.kapat")) {
                    allowed.add("close");
                }
                if (sender.hasPermission("veraxcore.chat.clear") || sender.hasPermission("veraxcore.sohbet.temizle")) {
                    allowed.add("clear");
                }
            }

            String current = args[0].toLowerCase();
            for (String sub : allowed) {
                if (sub.startsWith(current)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}

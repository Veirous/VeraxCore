package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.utils.AdventureUtil;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final Map<UUID, Long> reportCooldowns = new ConcurrentHashMap<>();

    public ReportCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Module check
        if (!plugin.getConfig().getBoolean("modules.report", plugin.getConfig().getBoolean("modules.rapor", true))) {
            sender.sendMessage(getMsg("messages.module-disabled", "<prefix>&cThis feature is currently disabled!"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Argument check
        if (args.length < 2) {
            String key = plugin.getLanguageManager().contains("messages.report-usage") ? "messages.report-usage" : "messages.rapor-kullanim";
            player.sendMessage(getMsg(key, "<prefix>&cUsage: &#1BA1FF/report <player> <reason>"));
            return true;
        }

        // Cooldown check (Bypass: veraxcore.report.bypass, veraxcore.admin or OP)
        if (!player.hasPermission("veraxcore.report.bypass") && !player.hasPermission("veraxcore.admin") && !player.isOp()) {
            long cooldownConfig = plugin.getConfig().getLong("settings.report-cooldown", 60L);
            long cooldownMs = cooldownConfig * 1000L;
            if (cooldownMs > 0 && reportCooldowns.containsKey(player.getUniqueId())) {
                long timePassed = System.currentTimeMillis() - reportCooldowns.get(player.getUniqueId());
                if (timePassed < cooldownMs) {
                    long remainingSecs = Math.max(1, (cooldownMs - timePassed + 999) / 1000);
                    String key = plugin.getLanguageManager().contains("messages.report-cooldown-warning") ? "messages.report-cooldown-warning" : "messages.rapor-cooldown";
                    String defaultMsg = "<prefix>&cYou must wait &e%remaining% &cseconds before submitting another report!";
                    String msg = getMsg(key, defaultMsg)
                            .replace("%remaining%", String.valueOf(remainingSecs))
                            .replace("<remaining>", String.valueOf(remainingSecs));
                    player.sendMessage(msg);
                    return true;
                }
            }
        }

        String targetName = args[0];

        // Target player existence check
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            targetPlayer = Bukkit.getPlayer(targetName);
        }

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            String key = plugin.getLanguageManager().contains("messages.player-not-found") ? "messages.player-not-found" : "messages.oyuncu-bulunamadi";
            player.sendMessage(getMsg(key, "<prefix>&cThe specified player was not found or is offline!"));
            return true;
        }

        // Self-report check
        if (player.getUniqueId().equals(targetPlayer.getUniqueId())) {
            String key = plugin.getLanguageManager().contains("messages.report-self") ? "messages.report-self" : "messages.rapor-kendini";
            player.sendMessage(getMsg(key, "<prefix>&cYou cannot report yourself!"));
            return true;
        }

        String finalReportedName = targetPlayer.getName();

        // Build reason string
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        // Record cooldown
        reportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        // Send confirmation message to reporter
        String key = plugin.getLanguageManager().contains("messages.report-success") ? "messages.report-success" : "messages.rapor-basarili";
        player.sendMessage(getMsg(key, "<prefix>&fYour report was &asuccessfully &fsubmitted to online staff."));

        // Notify online staff members
        String staffNotifyMsg = plugin.getLanguageManager().getString("messages.report-staff-notification", "&8[&cReport&8] &#1BA1FF{reporter} &f-> &#FF3333{reported}&f: &e{reason}");
        String formattedStaffMsg = plugin.translateHexColorCodes(staffNotifyMsg
                .replace("{reporter}", player.getName())
                .replace("{reported}", finalReportedName)
                .replace("{reason}", reason));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("veraxcore.report") || p.hasPermission("veraxcore.rapor") || p.isOp()) {
                AdventureUtil.sendMessage(p, formattedStaffMsg);
                try {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
            }
        }

        // Dispatch Discord Webhook
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sendDiscordReport(player.getName(), finalReportedName, reason);
        });

        return true;
    }

    private String getMsg(String path, String defaultMsg) {
        String msg = plugin.getMessage(path);
        if (msg == null || msg.isEmpty()) {
            String prefix = plugin.getConfig().getString("prefix", "&#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8»");
            msg = defaultMsg.replace("<prefix>", prefix).replace("%prefix%", prefix);
            return plugin.translateHexColorCodes(msg);
        }
        return msg;
    }

    private void sendDiscordReport(String reporter, String target, String reason) {
        try {
            String webhookUrl = plugin.getConfig().getString("report-webhook-url", plugin.getConfig().getString("rapor-webhook-url", ""));
            if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
                return;
            }

            String title = plugin.getLanguageManager().getString("report-log-settings.title", plugin.getConfig().getString("report-log-settings.title", plugin.getConfig().getString("rapor-log-settings.title", "🚨 New Player Report")));
            String colorHex = plugin.getConfig().getString("report-log-settings.color", plugin.getConfig().getString("rapor-log-settings.color", "#FF0000"));
            int colorInt = Integer.parseInt(colorHex.replace("#", ""), 16);
            String footer = plugin.getLanguageManager().getString("report-log-settings.footer", plugin.getConfig().getString("report-log-settings.footer", plugin.getConfig().getString("rapor-log-settings.footer", "VeraxCore • Report System")));
            
            String timeZoneStr = plugin.getConfig().getString("timezone", "Europe/Istanbul");
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timeZoneStr);
            } catch (Exception e) {
                zoneId = ZoneId.of("Europe/Istanbul");
            }
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            String formattedTime = now.format(dtf);

            String rawDesc = plugin.getLanguageManager().getString("report-log-settings.description", plugin.getConfig().getString("report-log-settings.description", "**Reporter:** `{reporter}`\n**Reported:** `{reported}`\n**Reason:** {reason}"));
            String description = rawDesc
                    .replace("{reporter}", reporter)
                    .replace("{reported}", target)
                    .replace("{reason}", reason)
                    .replace("{time}", formattedTime)
                    .replace("%reporter%", reporter)
                    .replace("%reported%", target)
                    .replace("%reason%", reason)
                    .replace("%time%", formattedTime);

            if (!description.contains(formattedTime)) {
                description = description + "\n**Date:** " + formattedTime;
            }

            footer = footer.replace("{time}", formattedTime).replace("%time%", formattedTime);
            if (!footer.contains(formattedTime) && !description.contains(formattedTime)) {
                footer = footer + " • " + formattedTime;
            }

            String thumbnail = "https://minotar.net/helm/" + reporter + "/100.png";

            String jsonPayload = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"" + DiscordWebhookSender.escapeJson(title) + "\","
                    + "\"color\": " + colorInt + ","
                    + "\"description\": \"" + DiscordWebhookSender.escapeJson(description) + "\","
                    + "\"thumbnail\": {\"url\": \"" + thumbnail + "\"},"
                    + "\"footer\": {\"text\": \"" + DiscordWebhookSender.escapeJson(footer) + "\"},"
                    + "\"timestamp\": \"" + now.toOffsetDateTime().toString() + "\""
                    + "}]"
                    + "}";

            DiscordWebhookSender.sendPayload(webhookUrl, jsonPayload, plugin.getLogger());
        } catch (Exception e) {
            plugin.getLogger().warning("[REPORT] Discord Webhook error: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.report", plugin.getConfig().getBoolean("modules.rapor", true))) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player p && p.getUniqueId().equals(online.getUniqueId())) {
                    continue;
                }
                if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    playerNames.add(online.getName());
                }
            }
            return playerNames;
        }

        return Collections.emptyList();
    }
}

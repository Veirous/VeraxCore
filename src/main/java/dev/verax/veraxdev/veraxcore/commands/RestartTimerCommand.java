package dev.verax.veraxdev.veraxcore.commands;

import dev.verax.veraxdev.veraxcore.VeraxCore;
import dev.verax.veraxdev.veraxcore.systems.AutoRestartSystem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RestartTimerCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public RestartTimerCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veraxcore.restarttimer") && !sender.hasPermission("veraxcore.admin")) {
            sender.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        AutoRestartSystem system = plugin.getAutoRestartSystem();
        if (system == null) {
            sender.sendMessage("§cAutoRestartSystem is not initialized.");
            return true;
        }

        if (args.length == 0) {
            String usage = plugin.getLanguageManager().getString("auto-restart.usage",
                    "&#FF5555Usage: &f/" + label + " <start <duration> | cancel | status> &7(e.g. /" + label + " start 5m)");
            sender.sendMessage(plugin.translateHexColorCodes(usage));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("cancel") || sub.equals("iptal") || sub.equals("stop") || sub.equals("durdur")) {
            if (!system.isManualRestartActive()) {
                String notActive = plugin.getLanguageManager().getString("auto-restart.manual-not-active",
                        "&#FF5555There is currently no active restart timer.");
                sender.sendMessage(plugin.translateHexColorCodes(notActive));
                return true;
            }

            system.cancelManualRestart(sender);
            return true;
        }

        if (sub.equals("status") || sub.equals("durum") || sub.equals("info") || sub.equals("bilgi")) {
            if (system.isManualRestartActive()) {
                long remaining = system.getManualRemainingSeconds();
                String timeStr = system.formatTime(remaining);
                String msg = plugin.getLanguageManager().getString("auto-restart.status",
                        "&#FFAA00[Restart] &fRemaining time: &#FFAA00%remaining_time% &7(%type%)")
                        .replace("%remaining_time%", timeStr)
                        .replace("%type%", "Manual");
                sender.sendMessage(plugin.translateHexColorCodes(msg));
            } else if (system.isEnabled()) {
                long remaining = system.getNextScheduledRemainingSeconds();
                if (remaining >= 0) {
                    String timeStr = system.formatTime(remaining);
                    String msg = plugin.getLanguageManager().getString("auto-restart.status",
                            "&#FFAA00[Restart] &fRemaining time: &#FFAA00%remaining_time% &7(%type%)")
                            .replace("%remaining_time%", timeStr)
                            .replace("%type%", "Scheduled");
                    sender.sendMessage(plugin.translateHexColorCodes(msg));
                } else {
                    sender.sendMessage(plugin.translateHexColorCodes("&#FF5555Scheduled restart time could not be found."));
                }
            } else {
                String notActive = plugin.getLanguageManager().getString("auto-restart.manual-not-active",
                        "&#FF5555There is currently no active restart timer.");
                sender.sendMessage(plugin.translateHexColorCodes(notActive));
            }
            return true;
        }

        // Start command (/restarttimer start <duration>)
        String durationArg;
        if (sub.equals("start")) {
            if (args.length < 2) {
                String usageStart = plugin.getLanguageManager().getString("auto-restart.usage-start",
                        "&#FF5555Usage: &f/" + label + " start <duration> &7(Examples: 30s, 5m, 10m, 1h)");
                sender.sendMessage(plugin.translateHexColorCodes(usageStart));
                return true;
            }
            durationArg = args[1];
        } else {
            // Also allow direct duration as fallback: /restarttimer 5m
            durationArg = args[0];
        }

        // Parse time parameter
        long seconds = system.parseDurationToSeconds(durationArg, -1L);
        if (seconds <= 0) {
            String invalidTime = plugin.getLanguageManager().getString("auto-restart.invalid-time",
                    "&#FF5555Invalid duration specified! &7(Examples: 30s, 5m, 10m, 1h)");
            sender.sendMessage(plugin.translateHexColorCodes(invalidTime));
            return true;
        }

        // Start countdown
        system.startManualRestart(seconds, sender.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veraxcore.restarttimer") && !sender.hasPermission("veraxcore.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = Arrays.asList("start", "cancel", "status");
            List<String> result = new ArrayList<>();
            for (String s : suggestions) {
                if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(s);
                }
            }
            return result;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("start")) {
                List<String> times = Arrays.asList("10s", "30s", "1m", "2m", "5m", "10m", "15m", "30m", "1h");
                List<String> result = new ArrayList<>();
                for (String t : times) {
                    if (t.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(t);
                    }
                }
                return result;
            }
        }

        return Collections.emptyList();
    }
}

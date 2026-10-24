package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.managers.MaintenanceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final MaintenanceManager maintenanceManager;

    public MaintenanceCommand(VeraxCore plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.maintenance", plugin.getConfig().getBoolean("modules.bakim", true))) {
            sender.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (!sender.hasPermission("veraxcore.maintenance") && !sender.hasPermission("veraxcore.bakim") && !sender.hasPermission("veraxcore.admin")) {
            sender.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("maintenance.usage"));
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);

        switch (sub) {
            case "on":
            case "aç":
            case "ac":
            case "enable":
            case "aktif":
                if (maintenanceManager.isMaintenanceActive()) {
                    sender.sendMessage(plugin.getMessage("maintenance.already-active"));
                    return true;
                }
                maintenanceManager.setMaintenanceActive(true);
                sender.sendMessage(plugin.getMessage("maintenance.enabled-success"));
                return true;

            case "off":
            case "kapat":
            case "disable":
            case "deaktif":
                if (!maintenanceManager.isMaintenanceActive() && !maintenanceManager.isCountdownActive()) {
                    sender.sendMessage(plugin.getMessage("maintenance.already-disabled"));
                    return true;
                }
                maintenanceManager.setMaintenanceActive(false);
                sender.sendMessage(plugin.getMessage("maintenance.disabled-success"));
                return true;

            case "start":
            case "başlat":
            case "baslat":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("maintenance.start-usage"));
                    return true;
                }

                if (maintenanceManager.isMaintenanceActive()) {
                    sender.sendMessage(plugin.getMessage("maintenance.already-active"));
                    return true;
                }

                long seconds = parseTime(args[1]);
                if (seconds <= 0) {
                    sender.sendMessage(plugin.getMessage("maintenance.invalid-time"));
                    return true;
                }

                boolean started = maintenanceManager.startCountdown(seconds, sender);
                if (started) {
                    String timeStr = maintenanceManager.formatTime(seconds);
                    String msg = plugin.getMessage("maintenance.countdown-started")
                                       .replace("%time%", timeStr)
                                       .replace("%remaining_time%", timeStr)
                                       .replace("%süre%", timeStr);
                    sender.sendMessage(msg);
                }
                return true;

            default:
                sender.sendMessage(plugin.getMessage("maintenance.usage"));
                return true;
        }
    }

    private long parseTime(String input) {
        if (input == null || input.trim().isEmpty()) return -1;
        input = input.trim().toLowerCase();

        try {
            // If only digits entered, consider as seconds
            if (input.matches("^\\d+$")) {
                return Long.parseLong(input);
            }

            long totalSeconds = 0;
            StringBuilder numBuf = new StringBuilder();

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (Character.isDigit(c)) {
                    numBuf.append(c);
                } else if (numBuf.length() > 0) {
                    long val = Long.parseLong(numBuf.toString());
                    numBuf.setLength(0);

                    if (c == 's' || c == 'z') { // seconds
                        totalSeconds += val;
                    } else if (c == 'm' || c == 'i') { // minutes
                        totalSeconds += val * 60;
                    } else if (c == 'h' || c == 't') { // hours
                        totalSeconds += val * 3600;
                    } else if (c == 'd' || c == 'g') { // days
                        totalSeconds += val * 86400;
                    }
                }
            }

            return totalSeconds > 0 ? totalSeconds : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veraxcore.maintenance") && !sender.hasPermission("veraxcore.bakim") && !sender.hasPermission("veraxcore.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = Arrays.asList("on", "off", "start");
            List<String> completions = new ArrayList<>();
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    completions.add(opt);
                }
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> suggestions = Arrays.asList("30s", "1m", "5m", "10m", "15m", "30m", "1h");
            List<String> completions = new ArrayList<>();
            for (String sug : suggestions) {
                if (sug.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    completions.add(sug);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}

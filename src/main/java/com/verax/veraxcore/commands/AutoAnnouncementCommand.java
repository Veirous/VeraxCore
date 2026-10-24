package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoAnnouncementCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public AutoAnnouncementCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.auto-announcement", plugin.getConfig().getBoolean("modules.otoduyuru", true))) {
            sender.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(plugin.getMessage("messages.auto-announcement-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("on") || sub.equals("aç") || sub.equals("ac") || sub.equals("aktif") || sub.equals("enable")) {
            plugin.getPlayerSettingsManager().setAutoAnnouncementEnabled(player.getUniqueId(), true);
            player.sendMessage(plugin.getMessage("messages.auto-announcement-toggle-on"));
            return true;
        } else if (sub.equals("off") || sub.equals("kapat") || sub.equals("kapalı") || sub.equals("kapali") || sub.equals("deaktif") || sub.equals("disable")) {
            plugin.getPlayerSettingsManager().setAutoAnnouncementEnabled(player.getUniqueId(), false);
            player.sendMessage(plugin.getMessage("messages.auto-announcement-toggle-off"));
            return true;
        } else {
            player.sendMessage(plugin.getMessage("messages.auto-announcement-usage"));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = Arrays.asList("on", "off");
            for (String opt : options) {
                if (opt.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(opt);
                }
            }
        }
        return completions;
    }
}

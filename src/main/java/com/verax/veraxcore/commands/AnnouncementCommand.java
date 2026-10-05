package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public AnnouncementCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Module Check (modules.announcement / modules.duyuru)
        if (!plugin.getConfig().getBoolean("modules.announcement", plugin.getConfig().getBoolean("modules.duyuru", true))) {
            sender.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (!sender.hasPermission("veraxcore.announcement") && !sender.hasPermission("veraxcore.duyuru")) {
            sender.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("messages.announcement-usage"));
            return true;
        }

        String announcementMessage = String.join(" ", args);
        String staticTitle = plugin.getMessage("announcement-settings.static-title");

        int fadeIn = plugin.getConfig().getInt("announcement-settings.fade-in", 500) / 50;
        int stay = plugin.getConfig().getInt("announcement-settings.stay", 3500) / 50;
        int fadeOut = plugin.getConfig().getInt("announcement-settings.fade-out", 1000) / 50;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(staticTitle, plugin.translateHexColorCodes(announcementMessage), fadeIn, stay, fadeOut);
        }

        sender.sendMessage(plugin.getMessage("messages.announcement-sent"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

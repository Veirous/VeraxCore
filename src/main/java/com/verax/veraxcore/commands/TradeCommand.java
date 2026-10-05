package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.listeners.TradeMenuListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final TradeMenuListener tradeMenu;

    public TradeCommand(VeraxCore plugin, TradeMenuListener tradeMenu) {
        this.plugin = plugin;
        this.tradeMenu = tradeMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Module check
        if (!plugin.getConfig().getBoolean("modules.trade", plugin.getConfig().getBoolean("modules.ticaret", true))) {
            String disabledMsg = plugin.getMessage("messages.module-disabled");
            sender.sendMessage(disabledMsg);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("veraxcore.trade") && !player.hasPermission("veraxcore.ticaret")) {
            player.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("on") || sub.equals("aç") || sub.equals("ac") || sub.equals("aktif") || sub.equals("enable")) {
                plugin.getPlayerSettingsManager().setTradeEnabled(player.getUniqueId(), true);
                player.sendMessage(plugin.getMessage("messages.trade-toggle-on"));
                return true;
            } else if (sub.equals("off") || sub.equals("kapat") || sub.equals("kapalı") || sub.equals("kapali") || sub.equals("deaktif") || sub.equals("disable")) {
                plugin.getPlayerSettingsManager().setTradeEnabled(player.getUniqueId(), false);
                player.sendMessage(plugin.getMessage("messages.trade-toggle-off"));
                return true;
            }
        }

        // Players with trade disabled cannot open the menu or participate in trades
        if (!plugin.getPlayerSettingsManager().isTradeEnabled(player.getUniqueId())) {
            String key = plugin.getLanguageManager().contains("messages.trade-disabled-self") ? "messages.trade-disabled-self" : "messages.trade-toggle-off";
            player.sendMessage(plugin.getMessage(key));
            return true;
        }

        tradeMenu.openTradeMenu(player);
        return true;
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

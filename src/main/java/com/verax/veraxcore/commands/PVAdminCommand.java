package com.verax.veraxcore.commands;

import com.verax.veraxcore.managers.PVManager;
import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PVAdminCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final PVManager pvManager;

    public PVAdminCommand(VeraxCore plugin, PVManager pvManager) {
        this.plugin = plugin;
        this.pvManager = pvManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by in-game staff members!");
            return true;
        }

        if (!player.hasPermission("veraxcore.pv.admin") && !player.hasPermission("veraxcore.admin") && !player.isOp()) {
            String msg = plugin.getMessage("messages.no-permission");
            if (!msg.isEmpty()) player.sendMessage(msg);
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("show")) {
            String msg = plugin.getMessage("messages.pv-invalid-args");
            if (msg.isEmpty()) msg = "§cUsage: /pvadmin show <player> [vault_number]";
            player.sendMessage(msg);
            return true;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("messages.pv-player-not-found");
            if (msg.isEmpty()) msg = "§cThe specified player was not found!";
            player.sendMessage(msg);
            return true;
        }

        if (args.length == 2) {
            pvManager.openSelectionMenu(player, target, true);
            return true;
        }

        try {
            int vaultId = Integer.parseInt(args[2]);
            if (vaultId < 1 || vaultId > 18) {
                player.sendMessage("§cVault number must be between 1 and 18!");
                return true;
            }

            pvManager.openVaultContent(player, target, vaultId, true);
        } catch (NumberFormatException e) {
            player.sendMessage("§cPlease enter a valid vault number (1-18)!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("show".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                completions.add("show");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            String sub = args[1].toLowerCase(java.util.Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(java.util.Locale.ROOT).startsWith(sub)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("show")) {
            String sub = args[2];
            for (int i = 1; i <= 18; i++) {
                String val = String.valueOf(i);
                if (val.startsWith(sub)) {
                    completions.add(val);
                }
            }
        }
        return completions;
    }
}

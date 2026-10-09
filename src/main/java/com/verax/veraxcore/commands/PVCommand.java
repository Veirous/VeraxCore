package com.verax.veraxcore.commands;

import com.verax.veraxcore.managers.PVManager;
import com.verax.veraxcore.VeraxCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PVCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private final PVManager pvManager;

    public PVCommand(VeraxCore plugin, PVManager pvManager) {
        this.plugin = plugin;
        this.pvManager = pvManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!plugin.getConfig().getBoolean("modules.pv", true)) {
            String msg = plugin.getMessage("messages.module-disabled");
            if (!msg.isEmpty()) player.sendMessage(msg);
            return true;
        }

        // /pv <number> -> open that vault directly
        if (args.length >= 1) {
            int vaultId;
            try {
                vaultId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // If not a number, open selection menu
                pvManager.openSelectionMenu(player, player, false);
                return true;
            }

            if (vaultId < 1 || vaultId > 18) {
                pvManager.openSelectionMenu(player, player, false);
                return true;
            }

            int maxVaults = pvManager.getMaxVaults(player);
            if (vaultId > maxVaults) {
                String msg = plugin.getMessage("messages.pv-locked-error");
                if (!msg.isEmpty()) player.sendMessage(msg);
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                return true;
            }

            pvManager.openVaultContent(player, player, vaultId, false);
            return true;
        }

        // /pv -> open vault selection menu
        pvManager.openSelectionMenu(player, player, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender instanceof Player player) {
            int maxVaults = pvManager.getMaxVaults(player);
            for (int i = 1; i <= maxVaults; i++) {
                String num = String.valueOf(i);
                if (num.startsWith(args[0])) {
                    completions.add(num);
                }
            }
        }
        return completions;
    }
}

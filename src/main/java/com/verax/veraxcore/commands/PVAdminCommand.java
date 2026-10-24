package com.verax.veraxcore.commands;

import com.verax.veraxcore.managers.PVManager;
import com.verax.veraxcore.utils.AdventureUtil;
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
            String msg = plugin.getMessage("messages.only-players");
            if (msg.isEmpty()) msg = "<red>This command can only be used by in-game staff members!";
            AdventureUtil.sendMessage(sender, msg);
            return true;
        }

        if (!player.hasPermission("veraxcore.pv.admin") && !player.hasPermission("veraxcore.admin") && !player.isOp()) {
            String msg = plugin.getMessage("messages.no-permission");
            if (!msg.isEmpty()) AdventureUtil.sendMessage(player, msg);
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("show")) {
            String msg = plugin.getMessage("messages.pv-invalid-args");
            if (msg.isEmpty()) msg = "<red>Usage: /pvadmin show <player> [vault_number]";
            AdventureUtil.sendMessage(player, msg);
            return true;
        }

        String targetName = args[1];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        OfflinePlayer target = onlineTarget;
        if (target == null) {
            try {
                target = Bukkit.getOfflinePlayerIfCached(targetName);
            } catch (NoSuchMethodError ignored) {
                target = Bukkit.getOfflinePlayer(targetName);
            }
        }

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("messages.pv-player-not-found");
            if (msg.isEmpty()) msg = "<red>The specified player was not found!";
            AdventureUtil.sendMessage(player, msg);
            return true;
        }

        if (args.length == 2) {
            pvManager.openSelectionMenu(player, target, true);
            return true;
        }

        int maxAllowed = pvManager.getConfiguredMaxVaults();
        try {
            int vaultId = Integer.parseInt(args[2]);
            if (vaultId < 1 || vaultId > maxAllowed) {
                String limitMsg = plugin.getMessage("messages.pv-invalid-vault-number");
                if (limitMsg == null || limitMsg.isEmpty()) {
                    limitMsg = "<red>Vault number must be between 1 and " + maxAllowed + "!";
                } else {
                    limitMsg = limitMsg.replace("%max%", String.valueOf(maxAllowed));
                }
                AdventureUtil.sendMessage(player, limitMsg);
                return true;
            }

            pvManager.openVaultContent(player, target, vaultId, true);
        } catch (NumberFormatException e) {
            String invalidMsg = plugin.getMessage("messages.pv-enter-valid-number");
            if (invalidMsg == null || invalidMsg.isEmpty()) {
                invalidMsg = "<red>Please enter a valid vault number (1-" + maxAllowed + ")!";
            } else {
                invalidMsg = invalidMsg.replace("%max%", String.valueOf(maxAllowed));
            }
            AdventureUtil.sendMessage(player, invalidMsg);
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
            int maxAllowed = pvManager.getConfiguredMaxVaults();
            for (int i = 1; i <= maxAllowed; i++) {
                String val = String.valueOf(i);
                if (val.startsWith(sub)) {
                    completions.add(val);
                }
            }
        }
        return completions;
    }
}

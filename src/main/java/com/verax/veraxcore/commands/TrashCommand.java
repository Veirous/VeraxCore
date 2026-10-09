package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.List;

public class TrashCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public TrashCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.trash", plugin.getConfig().getBoolean("modules.cop", true))) {
            sender.sendMessage(plugin.getMessage("messages.module-disabled"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = (Player) sender;

        String perm = plugin.getConfig().getString("trash-system.permission", plugin.getConfig().getString("cop-sistemi.permission", ""));
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            player.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        String rawTitle = plugin.getLanguageManager().getString("trash-system.title", plugin.getLanguageManager().getString("cop-sistemi.title", "&8Trash Bin"));
        String title = plugin.translateHexColorCodes(rawTitle);

        int slots = plugin.getConfig().getInt("trash-system.slots", plugin.getConfig().getInt("cop-sistemi.slots", 36));
        if (slots <= 0 || slots % 9 != 0 || slots > 54) {
            slots = 36;
        }

        Inventory inv = Bukkit.createInventory(null, slots, title);

        String soundName = plugin.getConfig().getString("trash-system.sound", plugin.getConfig().getString("cop-sistemi.sound", "BLOCK_CHEST_OPEN"));
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }

        player.openInventory(inv);

        String msgKey = plugin.getLanguageManager().contains("messages.trash-opened") ? "messages.trash-opened" : "messages.cop-acildi";
        String msg = plugin.getMessage(msgKey);
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

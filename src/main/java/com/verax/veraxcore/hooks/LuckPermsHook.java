package com.verax.veraxcore.hooks;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.listeners.ActivityListener;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LuckPermsHook {

    public static void register(VeraxCore plugin) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            ActivityListener.syncStaffState(plugin, player);
                        }
                    });
                }
            });

            luckPerms.getEventBus().subscribe(plugin, GroupDataRecalculateEvent.class, event -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ActivityListener.syncAllOnlinePlayers(plugin);
                });
            });
            plugin.logDebug("[LuckPerms Hook] UserDataRecalculateEvent and GroupDataRecalculateEvent registered successfully!");
        } catch (Throwable t) {
            plugin.logDebug("[LuckPerms Hook] Failed to register LuckPerms events: " + t.getMessage());
        }
    }
}

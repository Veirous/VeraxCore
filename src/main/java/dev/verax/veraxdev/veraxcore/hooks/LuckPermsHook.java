package dev.verax.veraxdev.veraxcore.hooks;

import dev.verax.veraxdev.veraxcore.VeraxCore;
import dev.verax.veraxdev.veraxcore.listeners.ActivityListener;
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
<<<<<<< HEAD:src/main/java/com/verax/veraxcore/hooks/LuckPermsHook.java
                com.verax.veraxcore.commands.StaffPlaytimeCommand.clearStaffRankCache();
=======
                dev.verax.veraxdev.veraxcore.commands.StaffPlaytimeCommand.clearStaffRankCache();
>>>>>>> 07a7f89 (src klasoru ve proje dosyaları eklendi):src/main/java/dev/verax/veraxdev/veraxcore/hooks/LuckPermsHook.java
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
<<<<<<< HEAD:src/main/java/com/verax/veraxcore/hooks/LuckPermsHook.java
                com.verax.veraxcore.commands.StaffPlaytimeCommand.clearStaffRankCache();
=======
                dev.verax.veraxdev.veraxcore.commands.StaffPlaytimeCommand.clearStaffRankCache();
>>>>>>> 07a7f89 (src klasoru ve proje dosyaları eklendi):src/main/java/dev/verax/veraxdev/veraxcore/hooks/LuckPermsHook.java
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

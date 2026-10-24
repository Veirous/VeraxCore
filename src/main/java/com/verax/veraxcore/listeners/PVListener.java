package com.verax.veraxcore.listeners;

import com.verax.veraxcore.managers.PVManager;
import com.verax.veraxcore.models.VaultContentHolder;
import com.verax.veraxcore.models.VaultMenuHolder;
import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PVListener implements Listener {

    private final VeraxCore plugin;
    private final PVManager pvManager;

    public PVListener(VeraxCore plugin, PVManager pvManager) {
        this.plugin = plugin;
        this.pvManager = pvManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null || topInv.getHolder() == null) return;

        if (topInv.getHolder() instanceof VaultMenuHolder holder) {
            event.setCancelled(true);

            if (event.getClickedInventory() != null && event.getClickedInventory().equals(topInv)) {
                int slot = event.getSlot();
                if (slot >= 0 && slot < pvManager.getConfiguredMaxVaults()) {
                    int vaultId = slot + 1;
                    OfflinePlayer target = Bukkit.getOfflinePlayer(holder.getTargetUuid());
                    int maxVaults = pvManager.getMaxVaults(target);

                    if (vaultId <= maxVaults) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                pvManager.openVaultContent(player, target, vaultId, holder.isAdminView());
                            }
                        });
                    } else {
                        String msg = plugin.getMessage("messages.pv-locked-error");
                        if (!msg.isEmpty()) {
                            com.verax.veraxcore.utils.AdventureUtil.sendMessage(player, msg);
                        }
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv != null && topInv.getHolder() instanceof VaultMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInv = event.getInventory();
        if (topInv != null && topInv.getHolder() instanceof VaultContentHolder holder) {
            java.util.UUID targetUuid = holder.getTargetUuid();
            int vaultId = holder.getVaultId();

            pvManager.saveVaultAsync(targetUuid, vaultId, topInv.getContents());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof VaultContentHolder holder) {
                pvManager.saveVaultAsync(holder.getTargetUuid(), holder.getVaultId(), top.getContents());
            }
        }
    }
}

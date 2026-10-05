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
                if (slot >= 0 && slot < 18) {
                    int vaultId = slot + 1;
                    OfflinePlayer target = Bukkit.getOfflinePlayer(holder.getTargetUuid());
                    int maxVaults = pvManager.getMaxVaults(target);

                    if (vaultId <= maxVaults) {
                        pvManager.openVaultContent(player, target, vaultId, holder.isAdminView());
                    } else {
                        String msg = plugin.getMessage("messages.pv-locked-error");
                        if (!msg.isEmpty()) {
                            player.sendMessage(msg);
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
            String vaultKey = targetUuid.toString() + ":" + vaultId;

            // Deep clone items on the main thread to prevent asynchronous concurrency issues
            ItemStack[] rawContents = topInv.getContents();
            ItemStack[] clonedContents = new ItemStack[rawContents.length];
            for (int i = 0; i < rawContents.length; i++) {
                clonedContents[i] = (rawContents[i] != null) ? rawContents[i].clone() : null;
            }

            // In Bukkit, the viewer triggering the close event is still in getViewers()
            if (topInv.getViewers().size() <= 1) {
                pvManager.getActiveVaults().remove(vaultKey);
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().saveVault(targetUuid, vaultId, clonedContents);
            });
        }
    }
}

package com.verax.veraxcore.managers;

import com.verax.veraxcore.models.VaultContentHolder;
import com.verax.veraxcore.models.VaultMenuHolder;
import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PVManager {

    private final VeraxCore plugin;

    public PVManager(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public int getMaxVaults(OfflinePlayer target) {
        if (target.isOnline()) {
            Player player = target.getPlayer();
            if (player != null) {
                if (player.hasPermission("veraxcore.pv.admin") || player.hasPermission("veraxcore.admin") || player.isOp()) {
                    return 18;
                }
                for (int i = 18; i >= 1; i--) {
                    if (player.hasPermission("veraxcore.pv.amount." + i)) {
                        return i;
                    }
                }
            }
        } else {
            try {
                net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
                net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(target.getUniqueId());
                if (user != null) {
                    for (int i = 18; i >= 1; i--) {
                        String perm = "veraxcore.pv.amount." + i;
                        boolean has = user.getCachedData().getPermissionData().checkPermission(perm).asBoolean();
                        if (has) return i;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return plugin.getConfig().getInt("pv-system.default-amount", plugin.getConfig().getInt("pv-sistemi.default-amount", 1));
    }

    public int getVaultSlotSize(OfflinePlayer target) {
        int defaultSlots = plugin.getConfig().getInt("pv-system.default-slots", plugin.getConfig().getInt("pv-sistemi.default-slots", 54));
        if (target.isOnline()) {
            Player player = target.getPlayer();
            if (player != null) {
                int[] availableSlots = {54, 45, 36, 27, 18, 9};
                for (int slots : availableSlots) {
                    if (player.hasPermission("veraxcore.pv.slot." + slots)) {
                        return slots;
                    }
                }
            }
        }
        if (defaultSlots <= 0 || defaultSlots % 9 != 0 || defaultSlots > 54) {
            defaultSlots = 54;
        }
        return defaultSlots;
    }

    public void openSelectionMenu(Player viewer, OfflinePlayer target, boolean isAdminView) {
        String targetName = (target.getName() != null) ? target.getName() : "Player";

        String titleKey = isAdminView ? "pv-system.admin-selection-menu-title" : "pv-system.selection-menu-title";
        if (!plugin.getLanguageManager().contains(titleKey)) {
            titleKey = isAdminView ? "pv-sistemi.admin-selection-menu-title" : "pv-sistemi.selection-menu-title";
        }
        String rawTitle = plugin.getLanguageManager().getString(titleKey, "&8Your Vaults");
        String title = plugin.translateHexColorCodes(rawTitle.replace("%player%", targetName).replace("%target%", targetName));

        VaultMenuHolder holder = new VaultMenuHolder(target.getUniqueId(), targetName, isAdminView);
        Inventory inv = Bukkit.createInventory(holder, 18, title);
        holder.setInventory(inv);

        int maxVaults = getMaxVaults(target);

        for (int i = 1; i <= 18; i++) {
            boolean isUnlocked = (i <= maxVaults);
            int menuSlot = i - 1;

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                if (isUnlocked) {
                    String rawName = plugin.getLanguageManager().getString("pv-system.unlocked-item.name", plugin.getLanguageManager().getString("pv-sistemi.unlocked-item.name", "&eVault &f#%vault_no%"));
                    String formattedName = rawName
                            .replace("%vault_no%", String.valueOf(i))
                            .replace("%deponumarası%", String.valueOf(i))
                            .replace("%depo_no%", String.valueOf(i))
                            .replace("%player%", targetName);
                    meta.setDisplayName(plugin.translateHexColorCodes(formattedName));

                    List<String> rawLore = plugin.getLanguageManager().getStringList("pv-system.unlocked-item.lore");
                    if (rawLore.isEmpty()) {
                        rawLore = plugin.getLanguageManager().getStringList("pv-sistemi.unlocked-item.lore");
                    }
                    if (rawLore.isEmpty()) {
                        rawLore = new ArrayList<>();
                        rawLore.add("&7• A small vault where you");
                        rawLore.add("&7• can store your items");
                        rawLore.add("");
                        rawLore.add("&eClick to open.");
                    }
                    List<String> lore = new ArrayList<>();
                    for (String l : rawLore) {
                        lore.add(plugin.translateHexColorCodes(l
                                .replace("%vault_no%", String.valueOf(i))
                                .replace("%deponumarası%", String.valueOf(i))
                                .replace("%depo_no%", String.valueOf(i))
                                .replace("%vault_id%", String.valueOf(i))
                                .replace("%player%", targetName)));
                    }
                    meta.setLore(lore);
                } else {
                    String rawName = plugin.getLanguageManager().getString("pv-system.locked-item.name", plugin.getLanguageManager().getString("pv-sistemi.locked-item.name", "&eVault &c(Locked)"));
                    String formattedName = rawName
                            .replace("%vault_no%", String.valueOf(i))
                            .replace("%deponumarası%", String.valueOf(i))
                            .replace("%depo_no%", String.valueOf(i))
                            .replace("%player%", targetName);
                    meta.setDisplayName(plugin.translateHexColorCodes(formattedName));

                    List<String> rawLore = plugin.getLanguageManager().getStringList("pv-system.locked-item.lore");
                    if (rawLore.isEmpty()) {
                        rawLore = plugin.getLanguageManager().getStringList("pv-sistemi.locked-item.lore");
                    }
                    if (rawLore.isEmpty()) {
                        rawLore = new ArrayList<>();
                        rawLore.add("&7• A small vault where you");
                        rawLore.add("&7• can store your items");
                        rawLore.add("");
                        rawLore.add("&cYou need VIP or a Vault Voucher to unlock");
                    }
                    List<String> lore = new ArrayList<>();
                    for (String l : rawLore) {
                        lore.add(plugin.translateHexColorCodes(l
                                .replace("%vault_no%", String.valueOf(i))
                                .replace("%deponumarası%", String.valueOf(i))
                                .replace("%depo_no%", String.valueOf(i))
                                .replace("%vault_id%", String.valueOf(i))
                                .replace("%player%", targetName)));
                    }
                    meta.setLore(lore);
                }
                item.setItemMeta(meta);
            }

            inv.setItem(menuSlot, item);
        }

        viewer.openInventory(inv);
    }

    private final java.util.Map<String, Inventory> activeVaults = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> pendingLoads = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public java.util.Map<String, Inventory> getActiveVaults() {
        return activeVaults;
    }

    public void saveAllActiveVaultsSync() {
        for (java.util.Map.Entry<String, Inventory> entry : activeVaults.entrySet()) {
            Inventory inv = entry.getValue();
            if (inv != null && inv.getHolder() instanceof VaultContentHolder holder) {
                ItemStack[] contents = inv.getContents();
                ItemStack[] cloned = new ItemStack[contents.length];
                for (int i = 0; i < contents.length; i++) {
                    cloned[i] = (contents[i] != null) ? contents[i].clone() : null;
                }
                plugin.getDatabaseManager().saveVault(holder.getTargetUuid(), holder.getVaultId(), cloned);
            }
        }
        activeVaults.clear();
        pendingLoads.clear();
    }

    public void openVaultContent(Player viewer, OfflinePlayer target, int vaultId, boolean isAdminView) {
        if (viewer == null || !viewer.isOnline()) return;

        int size = getVaultSlotSize(target);
        String targetName = (target.getName() != null) ? target.getName() : "Player";
        String vaultKey = target.getUniqueId().toString() + ":" + vaultId;

        // If vault is already open in memory, share the exact same inventory instance
        Inventory existingInv = activeVaults.get(vaultKey);
        if (existingInv != null) {
            viewer.openInventory(existingInv);
            sendVaultOpenMessage(viewer, targetName, vaultId, isAdminView);
            return;
        }

        // Debounce concurrent load requests
        if (pendingLoads.contains(vaultKey)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline()) {
                    openVaultContent(viewer, target, vaultId, isAdminView);
                }
            }, 3L);
            return;
        }

        pendingLoads.add(vaultKey);

        String titleKey = isAdminView ? "pv-system.admin-vault-menu-title" : "pv-system.vault-menu-title";
        if (!plugin.getLanguageManager().contains(titleKey)) {
            titleKey = isAdminView ? "pv-sistemi.admin-vault-menu-title" : "pv-sistemi.vault-menu-title";
        }
        String rawTitle = plugin.getLanguageManager().getString(titleKey, "&8Vault #%vault_no%");
        String title = plugin.translateHexColorCodes(rawTitle
                .replace("%vault_no%", String.valueOf(vaultId))
                .replace("%deponumarası%", String.valueOf(vaultId))
                .replace("%depo_no%", String.valueOf(vaultId))
                .replace("%player%", targetName)
                .replace("%target%", targetName));

        // Deserialize items from database asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ItemStack[] items = plugin.getDatabaseManager().loadVault(target.getUniqueId(), vaultId, size);

            // Open inventory on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingLoads.remove(vaultKey);
                if (!viewer.isOnline()) return;

                Inventory inv = activeVaults.get(vaultKey);
                if (inv == null) {
                    VaultContentHolder holder = new VaultContentHolder(target.getUniqueId(), targetName, vaultId, isAdminView);
                    inv = Bukkit.createInventory(holder, size, title);
                    holder.setInventory(inv);
                    inv.setContents(items);
                    activeVaults.put(vaultKey, inv);
                }

                viewer.openInventory(inv);
                sendVaultOpenMessage(viewer, targetName, vaultId, isAdminView);
            });
        });
    }

    private void sendVaultOpenMessage(Player viewer, String targetName, int vaultId, boolean isAdminView) {
        String msgKey = isAdminView ? "messages.pv-opened-admin" : "messages.pv-opened";
        String msg = plugin.getMessage(msgKey, targetName)
                .replace("%vault_no%", String.valueOf(vaultId))
                .replace("%deponumarası%", String.valueOf(vaultId))
                .replace("%depo_no%", String.valueOf(vaultId))
                .replace("%player%", targetName)
                .replace("%target%", targetName);
        if (!msg.isEmpty()) {
            viewer.sendMessage(msg);
        }
    }
}

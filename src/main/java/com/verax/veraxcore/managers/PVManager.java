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

    public int getConfiguredMaxVaults() {
        return Math.max(1, plugin.getConfig().getInt("pv-system.max-vaults", plugin.getConfig().getInt("pv-sistemi.max-vaults", 18)));
    }

    public int getMaxVaults(OfflinePlayer target) {
        int maxAllowed = getConfiguredMaxVaults();
        if (target.isOnline()) {
            Player player = target.getPlayer();
            if (player != null) {
                if (player.hasPermission("veraxcore.pv.admin") || player.hasPermission("veraxcore.admin") || player.isOp()) {
                    return maxAllowed;
                }
                for (int i = maxAllowed; i >= 1; i--) {
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
                    for (int i = maxAllowed; i >= 1; i--) {
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

        int maxVaults = getMaxVaults(target);
        int totalAllowed = getConfiguredMaxVaults();
        int invSize = Math.min(54, Math.max(18, ((totalAllowed + 8) / 9) * 9));

        VaultMenuHolder holder = new VaultMenuHolder(target.getUniqueId(), targetName, isAdminView);
        Inventory inv = Bukkit.createInventory(holder, invSize, title);
        holder.setInventory(inv);

        for (int i = 1; i <= totalAllowed && i <= invSize; i++) {
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
    private final java.util.Map<String, ItemStack[]> pendingSaves = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> activeSaveWorkers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> pendingLoads = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean isShuttingDown = false;

    public java.util.Map<String, Inventory> getActiveVaults() {
        return activeVaults;
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.isShuttingDown = shuttingDown;
    }

    /**
     * Safely queues a vault inventory for asynchronous database persistence.
     * Retains the inventory instance in memory during writes to completely eliminate
     * fast-reopen / macro duplication exploits.
     */
    public void saveVaultAsync(java.util.UUID targetUuid, int vaultId, ItemStack[] rawContents) {
        if (targetUuid == null || isShuttingDown) return;
        String vaultKey = targetUuid.toString() + ":" + vaultId;

        // Deep clone items on the main thread to prevent asynchronous concurrency issues
        ItemStack[] clonedContents = new ItemStack[rawContents.length];
        for (int i = 0; i < rawContents.length; i++) {
            clonedContents[i] = (rawContents[i] != null) ? rawContents[i].clone() : null;
        }

        // Put the latest snapshot into pendingSaves
        pendingSaves.put(vaultKey, clonedContents);

        // Ensure only one saving worker executes for this vaultKey at a time
        if (activeSaveWorkers.add(vaultKey)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    while (!isShuttingDown) {
                        // Always fetch the freshest snapshot in case newer modifications were made
                        ItemStack[] toSave = pendingSaves.get(vaultKey);
                        if (toSave == null) break;

                        boolean success = plugin.getDatabaseManager().saveVault(targetUuid, vaultId, toSave);
                        if (!success) {
                            plugin.getLogger().warning("[PV-Safety] Database write failed for vault " + vaultKey + ". Retaining in memory to prevent dupe.");
                            break;
                        }

                        // Remove only if pendingSaves still maps to the exact snapshot that was just persisted
                        pendingSaves.remove(vaultKey, toSave);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().severe("[PV-Safety] Unexpected error saving vault " + vaultKey + ": " + t.getMessage());
                } finally {
                    activeSaveWorkers.remove(vaultKey);

                    // Once all saves complete, if no viewers are viewing and no saves are pending, unload from memory on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!isShuttingDown && !pendingSaves.containsKey(vaultKey) && !activeSaveWorkers.contains(vaultKey)) {
                            Inventory inv = activeVaults.get(vaultKey);
                            if (inv != null && inv.getViewers().isEmpty()) {
                                activeVaults.remove(vaultKey);
                            }
                        }
                    });
                }
            });
        }
    }

    /**
     * Auto-saves all currently open Player Vaults through the unified async save pipeline.
     */
    public void autoSaveAllOpenVaults() {
        if (isShuttingDown) return;
        for (java.util.Map.Entry<String, Inventory> entry : activeVaults.entrySet()) {
            Inventory inv = entry.getValue();
            if (inv != null && inv.getHolder() instanceof VaultContentHolder holder) {
                if (!inv.getViewers().isEmpty()) {
                    saveVaultAsync(holder.getTargetUuid(), holder.getVaultId(), inv.getContents());
                }
            }
        }
    }

    /**
     * Synchronously persists all active and pending vaults to database before server shutdown.
     */
    public synchronized void saveAllActiveVaultsSync() {
        isShuttingDown = true;

        java.util.Map<String, ItemStack[]> toBatch = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, Inventory> entry : activeVaults.entrySet()) {
            Inventory inv = entry.getValue();
            if (inv != null && inv.getHolder() instanceof VaultContentHolder holder) {
                ItemStack[] contents = inv.getContents();
                ItemStack[] cloned = new ItemStack[contents.length];
                for (int i = 0; i < contents.length; i++) {
                    cloned[i] = (contents[i] != null) ? contents[i].clone() : null;
                }
                toBatch.put(entry.getKey(), cloned);
            }
        }

        for (java.util.Map.Entry<String, ItemStack[]> entry : pendingSaves.entrySet()) {
            toBatch.put(entry.getKey(), entry.getValue());
        }

        if (!toBatch.isEmpty() && plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().saveVaultsBatch(toBatch);
        }

        pendingSaves.clear();
        activeSaveWorkers.clear();
        activeVaults.clear();
        pendingLoads.clear();
    }

    public void openVaultContent(Player viewer, OfflinePlayer target, int vaultId, boolean isAdminView) {
        if (viewer == null || !viewer.isOnline() || isShuttingDown) return;

        int size = getVaultSlotSize(target);
        String targetName = (target.getName() != null) ? target.getName() : "Player";
        String vaultKey = target.getUniqueId().toString() + ":" + vaultId;

        // If vault is already open or cached in memory, share the exact same inventory instance
        // This avoids asynchronous database read races completely!
        Inventory existingInv = activeVaults.get(vaultKey);
        if (existingInv != null) {
            viewer.openInventory(existingInv);
            sendVaultOpenMessage(viewer, targetName, vaultId, isAdminView);
            return;
        }

        // Debounce if a save or load is currently in progress
        if (pendingSaves.containsKey(vaultKey) || activeSaveWorkers.contains(vaultKey) || pendingLoads.contains(vaultKey)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline() && !isShuttingDown) {
                    openVaultContent(viewer, target, vaultId, isAdminView);
                }
            }, 2L);
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
                if (!viewer.isOnline() || isShuttingDown) return;

                if (items == null) {
                    viewer.sendMessage(plugin.translateHexColorCodes("&c[✘] Database error: Could not load vault #" + vaultId + " safely. Please try again."));
                    return;
                }

                Inventory inv = activeVaults.get(vaultKey);
                boolean newlyCreated = false;
                if (inv == null) {
                    int finalSize = Math.min(54, Math.max(size, items.length));
                    if (finalSize % 9 != 0) {
                        finalSize = Math.min(54, ((finalSize / 9) + 1) * 9);
                    }
                    VaultContentHolder holder = new VaultContentHolder(target.getUniqueId(), targetName, vaultId, isAdminView);
                    inv = Bukkit.createInventory(holder, finalSize, title);
                    holder.setInventory(inv);
                    inv.setContents(items);
                    activeVaults.put(vaultKey, inv);
                    newlyCreated = true;
                }

                viewer.openInventory(inv);
                if (newlyCreated && inv.getViewers().isEmpty()) {
                    activeVaults.remove(vaultKey, inv);
                    return;
                }
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

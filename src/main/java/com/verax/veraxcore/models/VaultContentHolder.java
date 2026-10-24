package com.verax.veraxcore.models;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class VaultContentHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final String targetName;
    private final int vaultId;
    private final boolean isAdminView;
    private Inventory inventory;

    public VaultContentHolder(UUID targetUuid, String targetName, int vaultId, boolean isAdminView) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.vaultId = vaultId;
        this.isAdminView = isAdminView;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getVaultId() {
        return vaultId;
    }

    public boolean isAdminView() {
        return isAdminView;
    }
}

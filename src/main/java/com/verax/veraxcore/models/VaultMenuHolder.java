package com.verax.veraxcore.models;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class VaultMenuHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final String targetName;
    private final boolean isAdminView;
    private Inventory inventory;

    public VaultMenuHolder(UUID targetUuid, String targetName, boolean isAdminView) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
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

    public boolean isAdminView() {
        return isAdminView;
    }
}

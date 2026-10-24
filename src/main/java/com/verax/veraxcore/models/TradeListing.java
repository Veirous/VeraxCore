package com.verax.veraxcore.models;

import java.util.UUID;

public class TradeListing {
    private final UUID playerUUID;
    private final String playerName;
    private final String message;
    private final int slot;
    private final long expiryTime;

    public TradeListing(UUID playerUUID, String playerName, String message, int slot, long expiryTime) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.message = message;
        this.slot = slot;
        this.expiryTime = expiryTime;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public String getMessage() { return message; }
    public int getSlot() { return slot; }
    public long getExpiryTime() { return expiryTime; }
}

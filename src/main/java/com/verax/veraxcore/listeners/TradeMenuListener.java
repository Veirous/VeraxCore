package com.verax.veraxcore.listeners;

import com.verax.veraxcore.models.TradeListing;
import com.verax.veraxcore.models.TradeMenuHolder;
import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TradeMenuListener implements Listener {

    private final VeraxCore plugin;
    private final TradeChatListener chatListener;
    private org.bukkit.scheduler.BukkitTask menuUpdateTask;

    public TradeMenuListener(VeraxCore plugin, TradeChatListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
        startMenuUpdater();
    }

    public void cleanup() {
        if (menuUpdateTask != null) {
            menuUpdateTask.cancel();
            menuUpdateTask = null;
        }
    }

    // Live update timer for remaining time (%remaining_time% / %kalan_sure%) in open trade menus
    private void startMenuUpdater() {
        long refreshTicks = plugin != null ? plugin.getConfig().getLong("trade-visuals.menu-refresh-ticks", plugin.getConfig().getLong("ticaret-visuals.menu-refresh-ticks", 100L)) : 100L;
        menuUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory() != null) {
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (top != null) {
                        String title = p.getOpenInventory().getTitle().toLowerCase(java.util.Locale.ROOT);
                        if (top.getHolder() instanceof TradeMenuHolder 
                                || title.contains("ticaret") 
                                || title.contains("trade") 
                                || title.contains("handel")) {
                            updateMenuLore(top);
                        }
                    }
                }
            }
        }, refreshTicks, refreshTicks);
    }

    private void updateMenuLore(Inventory inv) {
        if (inv == null || chatListener == null) return;
        Map<Integer, TradeListing> active = chatListener.getActiveTrades();
        ItemStack cachedEmpty = null;

        for (int slot = 0; slot < 54 && slot < inv.getSize(); slot++) {
            if (isBorder(slot)) continue;

            TradeListing trade = active.get(slot);
            if (trade != null) {
                // Active trade: update or place player head
                ItemStack current = inv.getItem(slot);
                if (current == null || current.getType() != Material.PLAYER_HEAD) {
                    inv.setItem(slot, createTradeHead(trade));
                } else {
                    inv.setItem(slot, createTradeHead(trade));
                }
            } else {
                // No active trade: if item is a PLAYER_HEAD, trade expired, revert to empty slot paper!
                ItemStack current = inv.getItem(slot);
                if (current != null && current.getType() == Material.PLAYER_HEAD) {
                    if (cachedEmpty == null) {
                        cachedEmpty = createEmptySlotItem();
                    }
                    inv.setItem(slot, cachedEmpty);
                }
            }
        }
    }

    private ItemStack createTradeHead(TradeListing trade) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta == null) return skull;

        if (trade.getPlayerUUID() != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(trade.getPlayerUUID());
            skullMeta.setOwningPlayer(offlinePlayer);
        }

        String rawTitle = plugin.getLanguageManager().getString("trade-visuals.head-title",
                plugin.getLanguageManager().getString("trade-visuals.title",
                plugin.getLanguageManager().getString("ticaret-visuals.head-title",
                plugin.getLanguageManager().getString("ticaret-visuals.title", "&#1BA1FF%player% &fTrading &#1B4FFF/trade"))));

        List<String> rawLore = plugin.getLanguageManager().getStringList("trade-visuals.trade-lore");
        if (rawLore.isEmpty()) {
            rawLore = plugin.getLanguageManager().getStringList("ticaret-visuals.ticaret-lore");
        }
        if (rawLore.isEmpty()) {
            rawLore = Arrays.asList("&8", "&#1B4FFFMessage: &#1BA1FF%message%", "", "&fRemaining Time: &#1BA1FF%remaining_time%");
        }

        long remainingMillis = trade.getExpiryTime() - System.currentTimeMillis();
        long totalSecs = Math.max(0, remainingMillis / 1000);
        String remainingTimeStr = plugin.getLanguageManager() != null ? plugin.getLanguageManager().formatTime(totalSecs) : (totalSecs / 60) + "m " + (totalSecs % 60) + "s";

        String ownerName = trade.getPlayerName() != null ? trade.getPlayerName() : "";
        String message = trade.getMessage() != null ? trade.getMessage() : "";

        String formattedTitle = rawTitle.replace("%player%", ownerName)
                                        .replace("%owner%", ownerName)
                                        .replace("%mesaj%", message)
                                        .replace("%message%", message)
                                        .replace("%messages%", message)
                                        .replace("%kalan_sure%", remainingTimeStr)
                                        .replace("%remaining_time%", remainingTimeStr);
        skullMeta.setDisplayName(plugin.translateHexColorCodes(formattedTitle));

        List<String> formattedLore = new ArrayList<>();
        for (String line : rawLore) {
            String replaced = line.replace("%player%", ownerName)
                                  .replace("%owner%", ownerName)
                                  .replace("%mesaj%", message)
                                  .replace("%message%", message)
                                  .replace("%messages%", message)
                                  .replace("%kalan_sure%", remainingTimeStr)
                                  .replace("%remaining_time%", remainingTimeStr);
            formattedLore.add(plugin.translateHexColorCodes(replaced));
        }
        skullMeta.setLore(formattedLore);
        skull.setItemMeta(skullMeta);
        return skull;
    }

    public void openTradeMenu(Player player) {
        String titleStr = plugin != null ? plugin.getLanguageManager().getString("trade-visuals.menu-title", plugin.getLanguageManager().getString("ticaret-visuals.menu-title", "&8Trade Menu")) : "&8Trade Menu";
        TradeMenuHolder holder = new TradeMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.translateHexColorCodes(titleStr));
        holder.setInventory(inv);

        // Border glass pane item
        String glassMatName = plugin.getConfig().getString("trade-visuals.border-item", plugin.getConfig().getString("ticaret-visuals.border-item", "BLACK_STAINED_GLASS_PANE"));
        Material glassMat = Material.matchMaterial(glassMatName);
        if (glassMat == null) glassMat = Material.BLACK_STAINED_GLASS_PANE;

        ItemStack glass = new ItemStack(glassMat);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(plugin != null ? plugin.translateHexColorCodes(plugin.getLanguageManager().getString("trade-visuals.border-title", plugin.getLanguageManager().getString("ticaret-visuals.border-title", " "))) : " ");
            glass.setItemMeta(glassMeta);
        }

        ItemStack paper = createEmptySlotItem();

        for (int i = 0; i < 54; i++) {
            if (isBorder(i)) {
                inv.setItem(i, glass);
            } else if (chatListener.getActiveTrades().containsKey(i)) {
                TradeListing trade = chatListener.getActiveTrades().get(i);
                inv.setItem(i, createTradeHead(trade));
            } else {
                inv.setItem(i, paper);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createEmptySlotItem() {
        String emptyMatName = plugin.getConfig().getString("trade-visuals.empty-slot-item", plugin.getConfig().getString("ticaret-visuals.empty-slot-item", "PAPER"));
        Material emptyMat = Material.matchMaterial(emptyMatName);
        if (emptyMat == null) emptyMat = Material.PAPER;

        ItemStack paper = new ItemStack(emptyMat);
        ItemMeta paperMeta = paper.getItemMeta();
        if (paperMeta != null) {
            String paperTitle = plugin.getLanguageManager().getString("trade-visuals.empty-slot-title", plugin.getLanguageManager().getString("ticaret-visuals.empty-slot-title", "&eAdd Listing"));
            paperMeta.setDisplayName(plugin.translateHexColorCodes(paperTitle));

            List<String> emptyLore = plugin.getLanguageManager().getStringList("trade-visuals.empty-slot-lore");
            if (emptyLore.isEmpty()) {
                emptyLore = plugin.getLanguageManager().getStringList("ticaret-visuals.empty-slot-lore");
            }
            if (emptyLore.isEmpty()) {
                emptyLore = Arrays.asList("&7Click here to create a new", "&7trade listing.");
            }
            List<String> formattedEmptyLore = new ArrayList<>();
            for (String l : emptyLore) formattedEmptyLore.add(plugin.translateHexColorCodes(l));
            paperMeta.setLore(formattedEmptyLore);

            paper.setItemMeta(paperMeta);
        }
        return paper;
    }

    private boolean isBorder(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        boolean isTradeMenu = (topInv != null && topInv.getHolder() instanceof TradeMenuHolder);
        if (!isTradeMenu) {
            String viewTitle = org.bukkit.ChatColor.stripColor(event.getView().getTitle()).toLowerCase();
            isTradeMenu = viewTitle.contains("ticaret") || viewTitle.contains("trade") || viewTitle.contains("handel");
        }
        if (isTradeMenu) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54 || isBorder(slot)) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            // If a player trade head was clicked (execute configured actions)
            if (chatListener.getActiveTrades().containsKey(slot)) {
                if (!plugin.getPlayerSettingsManager().isTradeEnabled(player.getUniqueId())) {
                    player.closeInventory();
                    String key = plugin.getLanguageManager().contains("messages.trade-disabled-self") ? "messages.trade-disabled-self" : "messages.trade-toggle-off";
                    player.sendMessage(plugin.getMessage(key));
                    return;
                }

                TradeListing trade = chatListener.getActiveTrades().get(slot);
                if (trade != null) {
                    executeHeadClickActions(player, trade);
                }
                return;
            }

            // If empty slot item was clicked
            String emptyMatName = plugin.getConfig().getString("trade-visuals.empty-slot-item", plugin.getConfig().getString("ticaret-visuals.empty-slot-item", "PAPER"));
            Material emptyMat = Material.matchMaterial(emptyMatName);
            if (emptyMat == null) emptyMat = Material.PAPER;

            boolean isMapItem = (emptyMat == Material.MAP || emptyMat.name().contains("MAP")) && (clicked.getType() == Material.MAP || clicked.getType().name().contains("MAP"));
            boolean isEmptyItem = (clicked.getType() == emptyMat) || isMapItem;

            if (isEmptyItem) {
                if (!plugin.getPlayerSettingsManager().isTradeEnabled(player.getUniqueId())) {
                    player.closeInventory();
                    String key = plugin.getLanguageManager().contains("messages.trade-disabled-self") ? "messages.trade-disabled-self" : "messages.trade-toggle-off";
                    player.sendMessage(plugin.getMessage(key));
                    return;
                }

                if (chatListener.hasActiveTrade(player.getUniqueId())) {
                    player.closeInventory();
                    String key = plugin.getLanguageManager().contains("messages.trade-max-one") ? "messages.trade-max-one" : "messages.ticaret-max-one";
                    String msg = plugin.getMessage(key);
                    player.sendMessage(msg);
                    return;
                }

                if (chatListener.isGlobalCooldownActive()) {
                    player.closeInventory();
                    long remainingSecs = chatListener.getGlobalCooldownRemainingSecs();
                    String key = plugin.getLanguageManager().contains("messages.trade-global-cooldown-msg") ? "messages.trade-global-cooldown-msg" : "messages.ticaret-global-cooldown-msg";
                    String msg = plugin.getMessage(key)
                                       .replace("<remaining>", String.valueOf(remainingSecs))
                                       .replace("%remaining_seconds%", String.valueOf(remainingSecs))
                                       .replace("%remaining%", String.valueOf(remainingSecs))
                                       .replace("%kalan_saniye%", String.valueOf(remainingSecs));
                    player.sendMessage(msg);
                    return;
                }

                player.closeInventory();
                chatListener.setPendingSlot(player.getUniqueId(), slot);
                chatListener.addWaitingPlayer(player.getUniqueId());

                String key = plugin.getLanguageManager().contains("messages.trade-start") ? "messages.trade-start" : "messages.ticaret-basla";
                String msg = plugin.getMessage(key);
                player.sendMessage(msg);
            }
        }
    }

    private void executeHeadClickActions(Player clicker, TradeListing trade) {
        String clickerName = clicker.getName();
        String ownerName = trade.getPlayerName() != null ? trade.getPlayerName() : "";
        String message = trade.getMessage() != null ? trade.getMessage() : "";

        // 1. Notification message to clicker
        String msgConfig = plugin.getConfig().getString("trade-head-click-actions.message", plugin.getConfig().getString("ticaret-head-click-actions.message", ""));
        if (!msgConfig.isEmpty()) {
            String prefix = plugin.getConfig().getString("prefix", "");
            String finalMsg = msgConfig.replace("<prefix>", prefix)
                                       .replace("%prefix%", prefix)
                                       .replace("{clicker}", clickerName)
                                       .replace("{owner}", ownerName)
                                       .replace("{message}", message)
                                       .replace("{mesaj}", message);
            clicker.sendMessage(plugin.translateHexColorCodes(finalMsg));
        }

        // 2. Console commands
        List<String> consoleCmds = plugin.getConfig().getStringList("trade-head-click-actions.console-commands");
        if (consoleCmds.isEmpty()) {
            consoleCmds = plugin.getConfig().getStringList("ticaret-head-click-actions.console-commands");
        }
        for (String cmd : consoleCmds) {
            if (cmd == null || cmd.trim().isEmpty()) continue;
            String formattedCmd = cmd.replace("{clicker}", clickerName)
                                     .replace("{owner}", ownerName)
                                     .replace("{message}", message)
                                     .replace("{mesaj}", message);
            if (!formattedCmd.trim().isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
            }
        }

        // 3. Clicker player commands
        List<String> playerCmds = plugin.getConfig().getStringList("trade-head-click-actions.player-commands");
        if (playerCmds.isEmpty()) {
            playerCmds = plugin.getConfig().getStringList("ticaret-head-click-actions.player-commands");
        }
        for (String cmd : playerCmds) {
            if (cmd == null || cmd.trim().isEmpty()) continue;
            String formattedCmd = cmd.replace("{clicker}", clickerName)
                                     .replace("{owner}", ownerName)
                                     .replace("{message}", message)
                                     .replace("{mesaj}", message);
            if (!formattedCmd.trim().isEmpty()) {
                clicker.performCommand(formattedCmd);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv != null && topInv.getHolder() instanceof TradeMenuHolder) {
            event.setCancelled(true);
            return;
        }
        String viewTitle = org.bukkit.ChatColor.stripColor(event.getView().getTitle()).toLowerCase();
        if (viewTitle.contains("ticaret") || viewTitle.contains("trade") || viewTitle.contains("handel")) {
            event.setCancelled(true);
        }
    }
}

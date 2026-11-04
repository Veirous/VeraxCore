package dev.verax.veraxdev.veraxcore;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import dev.verax.veraxdev.veraxcore.listeners.ChatControlListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VeraxCoreMockTest {

    private ServerMock server;
    private VeraxCore plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(VeraxCore.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Plugin loads successfully in MockBukkit server environment")
    void testPluginLoad() {
        assertNotNull(plugin);
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getDatabaseManager());
        assertNotNull(plugin.getLanguageManager());
        assertNotNull(plugin.getChatControlListener());
    }

    @Test
    @DisplayName("ChatControlListener blocks message when chat is locked/closed")
    void testChatLockBlock() {
        ChatControlListener chatListener = plugin.getChatControlListener();
        assertNotNull(chatListener);

        // Lock chat
        chatListener.setChatMuted(true);
        assertTrue(chatListener.isChatMuted());

        PlayerMock player = server.addPlayer("NormalPlayer");

        // Normal player without bypass permission should be cancelled
        boolean cancelled = chatListener.shouldCancelChat(player, "Hello server!");
        assertTrue(cancelled, "Chat message should be cancelled when chat is locked");

        // Staff player with bypass permission should be allowed
        PlayerMock staff = server.addPlayer("StaffMember");
        staff.addAttachment(plugin, "veraxcore.chat.talk", true);
        boolean staffCancelled = chatListener.shouldCancelChat(staff, "Hello server!");
        assertFalse(staffCancelled, "Staff with talk permission should bypass chat lock");
    }

    @Test
    @DisplayName("ChatControlListener enforces chat cooldown/spam delay")
    void testChatCooldown() {
        ChatControlListener chatListener = plugin.getChatControlListener();
        chatListener.setChatMuted(false);

        PlayerMock player = server.addPlayer("Spammer");

        // First message passes
        boolean firstMessage = chatListener.shouldCancelChat(player, "First message");
        assertFalse(firstMessage, "First message should pass cooldown");

        // Immediate second message should be blocked by cooldown
        boolean secondMessage = chatListener.shouldCancelChat(player, "Second message");
        assertTrue(secondMessage, "Rapid second message should be blocked by cooldown");

        // Player with bypass permission
        PlayerMock vip = server.addPlayer("VipPlayer");
        vip.addAttachment(plugin, "veraxcore.chat.bypass", true);

        assertFalse(chatListener.shouldCancelChat(vip, "First"));
        assertFalse(chatListener.shouldCancelChat(vip, "Second (bypass)"), "Bypass permission should skip cooldown");
    }

    @Test
    @DisplayName("MaintenanceManager toggles maintenance mode and permissions")
    void testMaintenanceMode() {
        assertNotNull(plugin.getMaintenanceManager());
        assertFalse(plugin.getMaintenanceManager().isMaintenanceActive());

        plugin.getMaintenanceManager().setMaintenanceActive(true);
        assertTrue(plugin.getMaintenanceManager().isMaintenanceActive());

        plugin.getMaintenanceManager().setMaintenanceActive(false);
        assertFalse(plugin.getMaintenanceManager().isMaintenanceActive());
    }

    @Test
    @DisplayName("StaffChatCommand toggles and deduplicates chat events")
    void testStaffChatDeduplication() {
        dev.verax.veraxdev.veraxcore.commands.StaffChatCommand staffChat = new dev.verax.veraxdev.veraxcore.commands.StaffChatCommand(plugin);
        PlayerMock staff = server.addPlayer("Admin");
        staff.setOp(true);

        dev.verax.veraxdev.veraxcore.commands.StaffChatCommand.setStaffChatToggled(staff.getUniqueId(), true);
        assertTrue(dev.verax.veraxdev.veraxcore.commands.StaffChatCommand.isStaffChatToggled(staff.getUniqueId()));

        // Modern chat event
        io.papermc.paper.event.player.AsyncChatEvent modernEvent =
                new io.papermc.paper.event.player.AsyncChatEvent(true, staff, java.util.Collections.emptySet(),
                        io.papermc.paper.chat.ChatRenderer.defaultRenderer(),
                        net.kyori.adventure.text.Component.text("Selam"),
                        net.kyori.adventure.text.Component.text("Selam"),
                        null);
        staffChat.onModernChat(modernEvent);
        assertTrue(modernEvent.isCancelled());

        // Immediate legacy chat event for the same message
        org.bukkit.event.player.AsyncPlayerChatEvent legacyEvent =
                new org.bukkit.event.player.AsyncPlayerChatEvent(true, staff, "Selam", java.util.Collections.emptySet());
        staffChat.onPlayerChat(legacyEvent);
        assertTrue(legacyEvent.isCancelled());
    }

    @Test
    @DisplayName("TradeChatListener deduplicates chat events on character limits")
    void testTradeChatDeduplication() {
        dev.verax.veraxdev.veraxcore.listeners.TradeChatListener tradeChat = new dev.verax.veraxdev.veraxcore.listeners.TradeChatListener(plugin);
        PlayerMock player = server.addPlayer("Trader");
        tradeChat.setPendingSlot(player.getUniqueId(), 0);
        tradeChat.addWaitingPlayer(player.getUniqueId());

        // Player sends message that is too short ("hi" < 3)
        io.papermc.paper.event.player.AsyncChatEvent modernEvent =
                new io.papermc.paper.event.player.AsyncChatEvent(true, player, java.util.Collections.emptySet(),
                        io.papermc.paper.chat.ChatRenderer.defaultRenderer(),
                        net.kyori.adventure.text.Component.text("hi"),
                        net.kyori.adventure.text.Component.text("hi"),
                        null);
        tradeChat.onModernChat(modernEvent);
        assertTrue(modernEvent.isCancelled());

        // Check messages received so far
        String firstWarning = player.nextMessage();
        assertNotNull(firstWarning, "Player should receive a warning that message is too short");

        // Immediate legacy chat event for the same message
        org.bukkit.event.player.AsyncPlayerChatEvent legacyEvent =
                new org.bukkit.event.player.AsyncPlayerChatEvent(true, player, "hi", java.util.Collections.emptySet());
        tradeChat.onChat(legacyEvent);
        assertTrue(legacyEvent.isCancelled());

        // Player should NOT have received a second warning message!
        assertNull(player.nextMessage(), "Duplicate event should not produce a second warning message");
        tradeChat.cleanup();
    }

    @Test
    @DisplayName("SpawnListener allows head rotation and ignores mouse movement during teleport")
    void testSpawnTeleportHeadRotationAllowed() {
        dev.verax.veraxdev.veraxcore.listeners.SpawnListener spawnListener = new dev.verax.veraxdev.veraxcore.listeners.SpawnListener(plugin);
        PlayerMock player = server.addPlayer("TeleportingPlayer");
        org.bukkit.Location loc = player.getLocation();

        // Start teleport
        dev.verax.veraxdev.veraxcore.commands.SpawnCommand spawnCmd = new dev.verax.veraxdev.veraxcore.commands.SpawnCommand(plugin);
        plugin.getConfig().set("teleport-delay", 5);
        plugin.saveLocationToConfig("spawn-location", loc);
        spawnCmd.onCommand(player, null, "spawn", new String[0]);
        assertTrue(dev.verax.veraxdev.veraxcore.commands.SpawnCommand.isTeleporting(player.getUniqueId()));

        // Simulate looking around (changing yaw and pitch, but identical XYZ)
        org.bukkit.Location rotLoc = loc.clone();
        rotLoc.setYaw(180.0f);
        rotLoc.setPitch(45.0f);
        org.bukkit.event.player.PlayerMoveEvent lookEvent = new org.bukkit.event.player.PlayerMoveEvent(player, loc, rotLoc);
        spawnListener.onPlayerMove(lookEvent);

        // Teleport should STILL be active!
        assertTrue(dev.verax.veraxdev.veraxcore.commands.SpawnCommand.isTeleporting(player.getUniqueId()),
                "Teleportation must NOT be cancelled when player merely looks around / rotates camera");

        // Simulate actual physical movement (walking 2 blocks away)
        org.bukkit.Location walkLoc = loc.clone().add(2.0, 0, 0);
        org.bukkit.event.player.PlayerMoveEvent moveEvent = new org.bukkit.event.player.PlayerMoveEvent(player, loc, walkLoc);
        spawnListener.onPlayerMove(moveEvent);

        // Teleport should now be cancelled!
        assertFalse(dev.verax.veraxdev.veraxcore.commands.SpawnCommand.isTeleporting(player.getUniqueId()),
                "Teleportation should be cancelled when player actually moves/walks away");
    }

    @Test
    @DisplayName("PVManager retains active vault in memory upon close to eliminate fast-reopen dupe")
    void testPlayerVaultFastReopenDupeProtection() {
        assertNotNull(plugin.getPvManager());
        PlayerMock player = server.addPlayer("VaultUser");
        player.addAttachment(plugin, "veraxcore.pv.amount.5", true);

        // Open vault
        plugin.getPvManager().openVaultContent(player, player, 1, false);
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performOneTick();

        String vaultKey = player.getUniqueId().toString() + ":1";
        org.bukkit.inventory.Inventory inv = plugin.getPvManager().getActiveVaults().get(vaultKey);
        assertNotNull(inv, "Active vault must be registered in activeVaults");

        // Player places a Diamond in slot 0
        org.bukkit.inventory.ItemStack diamond = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 32);
        inv.setItem(0, diamond);

        // Close vault -> triggers async save pipeline
        player.closeInventory();

        // FAST REOPEN: Immediately check if activeVaults still holds the in-memory inventory
        // (Before the fix, activeVaults was immediately removed, causing database race condition dupe)
        org.bukkit.inventory.Inventory cachedInv = plugin.getPvManager().getActiveVaults().get(vaultKey);
        assertNotNull(cachedInv, "Active vault must remain in memory during and immediately after save to prevent stale DB reads");
        assertNotNull(cachedInv.getItem(0));
        assertEquals(org.bukkit.Material.DIAMOND, cachedInv.getItem(0).getType());
        assertEquals(32, cachedInv.getItem(0).getAmount());

        // Shutdown sync save should safely flush all vaults
        plugin.getPvManager().saveAllActiveVaultsSync();
        assertTrue(plugin.getPvManager().getActiveVaults().isEmpty(), "Active vaults should be cleared after sync save");

        // Verify that database now holds the saved diamond item
        org.bukkit.inventory.ItemStack[] loaded = plugin.getDatabaseManager().loadVault(player.getUniqueId(), 1, 54);
        assertNotNull(loaded[0]);
        assertEquals(org.bukkit.Material.DIAMOND, loaded[0].getType());
        assertEquals(32, loaded[0].getAmount());
    }

    @Test
    @DisplayName("WordActionRule matches whole words and avoids substring false-positives (Scunthorpe problem)")
    void testWordActionsWholeWordMatching() {
        ChatControlListener chatListener = plugin.getChatControlListener();
        assertNotNull(chatListener);
        chatListener.setChatMuted(false);
        plugin.getConfig().set("word-actions.example_action.cancel-chat", true);
        chatListener.reloadWordActions();

        PlayerMock player = server.addPlayer("WordTester");
        player.addAttachment(plugin, "veraxcore.chat.bypass", true);

        // Innocent words containing "mal" or "sa" as substrings must NOT be blocked
        assertFalse(chatListener.shouldCancelChat(player, "Bugün hava normal"), "'normal' should not trigger 'mal' rule");
        assertFalse(chatListener.shouldCancelChat(player, "Elmalı turta çok güzel"), "'elmalı' should not trigger 'mal' rule");
        assertFalse(chatListener.shouldCancelChat(player, "Yeni malzeme aldım"), "'malzeme' should not trigger 'mal' rule");
        assertFalse(chatListener.shouldCancelChat(player, "Kemal oyuna girdi"), "'Kemal' should not trigger 'mal' rule");

        // Actual standalone forbidden word "mal" MUST be blocked (cancel-chat: true in default config)
        assertTrue(chatListener.shouldCancelChat(player, "mal"), "Standalone 'mal' must trigger word action and cancel chat");
        assertTrue(chatListener.shouldCancelChat(player, "sen mal mısın?"), "'mal' as a whole word in a sentence must be cancelled");
        assertTrue(chatListener.shouldCancelChat(player, "MAL!"), "Uppercase 'MAL!' with punctuation must be cancelled");
    }

    @Test
    @DisplayName("ActivityListener.flushOnlineSessionsToDatabase preserves weekly/monthly/total time across daily reset")
    void testFlushOnlineSessionsPreservesPlaytimeAcrossDailyReset() {
        PlayerMock staff = server.addPlayer("ActiveStaff");
        java.util.UUID uuid = staff.getUniqueId();

        // Simulate 180 seconds of active online session not yet flushed to database
        long fakeJoinTime = System.currentTimeMillis() - 180_000L;
        dev.verax.veraxdev.veraxcore.listeners.ActivityListener.loginTimes.put(uuid, fakeJoinTime);

        // Flush sessions before daily reset (as done at 00:00 midnight)
        dev.verax.veraxdev.veraxcore.listeners.ActivityListener.flushOnlineSessionsToDatabase(plugin);

        // Verify all periods received the 180 seconds before clearDailySeconds
        assertTrue(plugin.getDatabaseManager().getAllSeconds("daily").getOrDefault(uuid, 0) >= 180);
        assertTrue(plugin.getDatabaseManager().getAllSeconds("weekly").getOrDefault(uuid, 0) >= 180);
        assertTrue(plugin.getDatabaseManager().getAllSeconds("monthly").getOrDefault(uuid, 0) >= 180);
        assertTrue(plugin.getDatabaseManager().getAllSeconds("total").getOrDefault(uuid, 0) >= 180);

        // Clear daily seconds
        plugin.getDatabaseManager().clearDailySeconds();

        // Daily must be 0, while weekly, monthly, and total must retain the 180 seconds
        assertEquals(0, plugin.getDatabaseManager().getAllSeconds("daily").getOrDefault(uuid, 0));
        assertTrue(plugin.getDatabaseManager().getAllSeconds("weekly").getOrDefault(uuid, 0) >= 180);
        assertTrue(plugin.getDatabaseManager().getAllSeconds("monthly").getOrDefault(uuid, 0) >= 180);
        assertTrue(plugin.getDatabaseManager().getAllSeconds("total").getOrDefault(uuid, 0) >= 180);
    }

    @Test
    @DisplayName("Verification command aliases (/verify, /dogrula, /doğrula) are allowed during pending verification while other commands are blocked")
    void testVerificationAliasesAllowed() {
        dev.verax.veraxdev.veraxcore.systems.VerificationSystem verif = plugin.getVerificationSystem();
        assertNotNull(verif);

        PlayerMock player = server.addPlayer("VerifyUser");

        // Simulate a pending verification for this player
        dev.verax.veraxdev.veraxcore.systems.VerificationSystem.PendingVerification pending =
                new dev.verax.veraxdev.veraxcore.systems.VerificationSystem.PendingVerification("123456", 120, 3, null);
        try {
            java.lang.reflect.Field field = dev.verax.veraxdev.veraxcore.systems.VerificationSystem.class.getDeclaredField("pendingVerifications");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, dev.verax.veraxdev.veraxcore.systems.VerificationSystem.PendingVerification> map =
                    (java.util.Map<java.util.UUID, dev.verax.veraxdev.veraxcore.systems.VerificationSystem.PendingVerification>) field.get(verif);
            map.put(player.getUniqueId(), pending);
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }

        // Test non-verify command -> MUST be cancelled
        org.bukkit.event.player.PlayerCommandPreprocessEvent eventBlock =
                new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/spawn");
        verif.onCommand(eventBlock);
        assertTrue(eventBlock.isCancelled(), "/spawn should be blocked during pending verification");

        // Test /verify -> MUST NOT be cancelled
        org.bukkit.event.player.PlayerCommandPreprocessEvent eventVerify =
                new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/verify 123456");
        verif.onCommand(eventVerify);
        assertFalse(eventVerify.isCancelled(), "/verify should NOT be blocked");

        // Test /dogrula -> MUST NOT be cancelled
        org.bukkit.event.player.PlayerCommandPreprocessEvent eventDogrula =
                new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/dogrula 123456");
        verif.onCommand(eventDogrula);
        assertFalse(eventDogrula.isCancelled(), "/dogrula should NOT be blocked");

        // Test /doğrula -> MUST NOT be cancelled
        org.bukkit.event.player.PlayerCommandPreprocessEvent eventDogrulaTr =
                new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/doğrula 123456");
        verif.onCommand(eventDogrulaTr);
        assertFalse(eventDogrulaTr.isCancelled(), "/doğrula should NOT be blocked");

        // Test /veraxcore:verify -> MUST NOT be cancelled
        org.bukkit.event.player.PlayerCommandPreprocessEvent eventNamespaced =
                new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/veraxcore:verify 123456");
        verif.onCommand(eventNamespaced);
        assertFalse(eventNamespaced.isCancelled(), "/veraxcore:verify should NOT be blocked");
    }

    @Test
    @DisplayName("AutoRestart configuration and language customization keys are properly defined and loaded")
    void testAutoRestartConfigCustomization() {
        assertTrue(plugin.getConfig().getBoolean("auto-restart.bossbar.enabled", false));
        assertTrue(plugin.getConfig().getBoolean("auto-restart.broadcast.enabled", false));
        assertFalse(plugin.getConfig().getStringList("auto-restart.broadcast.intervals").isEmpty());
        assertTrue(plugin.getConfig().getBoolean("auto-restart.title.enabled", false));
        assertEquals(5, plugin.getConfig().getInt("auto-restart.title.countdown-seconds", -1));
        assertTrue(plugin.getConfig().getBoolean("auto-restart.sound.enabled", false));
        assertEquals("BLOCK_NOTE_BLOCK_PLING", plugin.getConfig().getString("auto-restart.sound.name", ""));

        assertNotNull(plugin.getLanguageManager().getString("auto-restart.broadcast", null));
        assertNotNull(plugin.getLanguageManager().getString("auto-restart.title", null));
        assertNotNull(plugin.getLanguageManager().getString("auto-restart.subtitle", null));
    }

    @Test
    @DisplayName("Prefix placeholder %prefix% and <prefix> are dynamically replaced wherever placed")
    void testDynamicPrefixPlacement() {
        dev.verax.veraxdev.veraxcore.managers.LanguageManager lm = plugin.getLanguageManager();
        assertNotNull(lm);
        String prefix = lm.getPrefix();
        assertNotNull(prefix);
        assertFalse(prefix.isEmpty());

        // At beginning
        String start = lm.applyPrefix("%prefix% &aHello");
        assertEquals(prefix + " &aHello", start);

        // In the middle
        String middle = lm.applyPrefix("&8[&6Alert&8] %prefix% &fNotice");
        assertEquals("&8[&6Alert&8] " + prefix + " &fNotice", middle);

        // At the end
        String end = lm.applyPrefix("&7Server restarted %prefix%");
        assertEquals("&7Server restarted " + prefix, end);

        // Without prefix placeholder
        String none = lm.applyPrefix("&cNo prefix here");
        assertEquals("&cNo prefix here", none);

        // Angle bracket format <prefix>
        String angle = lm.applyPrefix("<prefix> &bTest");
        assertEquals(prefix + " &bTest", angle);

        // Test through getString with default
        String fromDefault = lm.getString("non.existent.path", "%prefix% &cDefault Message");
        assertEquals(prefix + " &cDefault Message", fromDefault);
    }
}

package com.verax.veraxcore.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdaterTest {

    @Test
    @DisplayName("Auto-migrates renamed key and preserves custom user webhook")
    void testAutoMigrationRenamedKey() throws Exception {
        String template = "# 🔗 DISCORD WEBHOOK URLS\n" +
                "staff-time-webhook-url: \"YOUR_WEBHOOK_URL_HERE\" # webhook for staff activity logs\n" +
                "chat-log-webhook-url: \"YOUR_CHAT_LOG_WEBHOOK_URL_HERE\" # webhook for chat management logs\n" +
                "target-seconds: 7200 # staff activity target in seconds\n";

        String disk = "# 🔗 DISCORD WEBHOOK URLS\n" +
                "webhook-url: \"https://discord.com/api/webhooks/custom123\" # webhook for staff activity logs\n" +
                "chat-log-webhook-url: \"YOUR_CHAT_LOG_WEBHOOK_URL_HERE\"\n" +
                "target-seconds: 3600\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        for (String k : diskYaml.getKeys(true)) {
            if (!diskYaml.isConfigurationSection(k) && !jarYaml.contains(k)) {
                obsolete.put(k, diskYaml.get(k));
            }
        }
        assertEquals(1, obsolete.size());
        assertTrue(obsolete.containsKey("webhook-url"));

        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        // 1. staff-time-webhook-url should have the user's custom URL migrated from webhook-url
        assertTrue(result.contains("staff-time-webhook-url: \"https://discord.com/api/webhooks/custom123\" # webhook for staff activity logs"));
        // 2. target-seconds should preserve user's 3600
        assertTrue(result.contains("target-seconds: 3600 # staff activity target in seconds"));
        // 3. Old webhook-url should not appear in the synced file
        assertFalse(result.contains("\nwebhook-url:"));
        // 4. consumed should have recorded webhook-url
        assertTrue(consumed.contains("webhook-url"));
    }

    @Test
    @DisplayName("Automatically injects new settings from template into existing section")
    void testNewSettingInjection() throws Exception {
        String template = "modules:\n" +
                "  staff-time: true # staff time\n" +
                "  new-feature: false # newly added feature in this update\n";

        String disk = "modules:\n" +
                "  staff-time: false\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        // staff-time should preserve user's false
        assertTrue(result.contains("  staff-time: false # staff time"));
        // new-feature should be added with template default and comment
        assertTrue(result.contains("  new-feature: false # newly added feature in this update"));
    }

    @Test
    @DisplayName("Preserves custom list items from disk")
    void testListPreservation() throws Exception {
        String template = "allowed-ranks:\n" +
                "  - \"helper\"\n" +
                "  - \"admin\"\n";

        String disk = "allowed-ranks:\n" +
                "  - \"helper\"\n" +
                "  - \"moderator\"\n" +
                "  - \"admin\"\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        assertTrue(result.contains("- \"moderator\""));
    }

    @Test
    @DisplayName("Finds inline comments correctly without tripping on hashes inside strings")
    void testInlineCommentIndex() {
        assertEquals(-1, ConfigUpdater.findInlineCommentIndex("\"#1B4FFF\""));
        assertEquals(-1, ConfigUpdater.findInlineCommentIndex("\"Hello # World\""));
        assertEquals(11, ConfigUpdater.findInlineCommentIndex("\"hello\"    # actual comment"));
        assertEquals(6, ConfigUpdater.findInlineCommentIndex("false # developer mode"));
    }

    @Test
    @DisplayName("Always stamps latest config-version from template")
    void testConfigVersionStamping() throws Exception {
        String template = "# Internal version\n" +
                "config-version: 2 # do not edit\n" +
                "debug: false\n";

        String disk = "config-version: 1\n" +
                "debug: true\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        // Version should be 2 from template
        assertTrue(result.contains("config-version: 2 # do not edit"));
        // User's debug: true should be preserved
        assertTrue(result.contains("debug: true"));
    }

    @Test
    @DisplayName("Migrates trade length, migrates session-duration to session-timeout, and drops obsolete mysql.use-ssl")
    void testMigrationAndIgnoredKeys() throws Exception {
        String template = "settings:\n" +
                "  trade-cooldown: 300\n" +
                "  trade-min-length: 3\n" +
                "  trade-max-length: 100\n" +
                "verification-system:\n" +
                "  session-timeout-hours: 6\n" +
                "mysql:\n" +
                "  host: \"localhost\"\n";

        String disk = "settings:\n" +
                "  trade-cooldown: 200\n" +
                "  trade-min-length: 5\n" +
                "  trade-max-length: 80\n" +
                "verification-system:\n" +
                "  session-duration-hours: 3\n" +
                "mysql:\n" +
                "  host: \"127.0.0.1\"\n" +
                "  use-ssl: false\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        for (String k : diskYaml.getKeys(true)) {
            if (!diskYaml.isConfigurationSection(k) && !jarYaml.contains(k)) {
                obsolete.put(k, diskYaml.get(k));
            }
        }
        // Obsolete keys on disk should be session-duration-hours and mysql.use-ssl
        assertEquals(2, obsolete.size());
        assertTrue(obsolete.containsKey("verification-system.session-duration-hours"));
        assertTrue(obsolete.containsKey("mysql.use-ssl"));

        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        // 1. User's trade settings are preserved in settings:
        assertTrue(result.contains("trade-cooldown: 200"));
        assertTrue(result.contains("trade-min-length: 5"));
        assertTrue(result.contains("trade-max-length: 80"));

        // 2. session-duration-hours is migrated to session-timeout-hours under verification-system:
        assertTrue(result.contains("session-timeout-hours: 3"));
        assertTrue(consumed.contains("verification-system.session-duration-hours"));

        // 3. mysql.use-ssl is NOT in synchronized content
        assertFalse(result.contains("use-ssl"));

        // 4. Checking unhandled keys: mysql.use-ssl should NOT be in unhandled
        Map<String, Object> unhandled = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : obsolete.entrySet()) {
            if (!consumed.contains(entry.getKey())) {
                String lower = entry.getKey().toLowerCase();
                if (!lower.contains("use-ssl") && !lower.contains("ssl-enabled")) {
                    unhandled.put(entry.getKey(), entry.getValue());
                }
            }
        }
        assertTrue(unhandled.isEmpty(), "No unhandled keys should remain");
    }

    @Test
    @DisplayName("Block scalars with empty lines are not duplicated and next sections are preserved")
    void testMultilineBlockScalarWithEmptyLines() throws Exception {
        String template = "maintenance:\n" +
                "  kick-screen: |\n" +
                "    &8__________________________________________________\n" +
                "    \n" +
                "    &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe\n" +
                "    &#FF3333&lSunucu Bakımda\n" +
                "    \n" +
                "    Discord: discord.gg/yourdiscord\n" +
                "    &8__________________________________________________\n" +
                "\n" +
                "# ==============================================================================\n" +
                "auto-restart:\n" +
                "  bossbar-text: \"Template Restart\"\n";

        String disk = "maintenance:\n" +
                "  kick-screen: |\n" +
                "    &8__________________________________________________\n" +
                "    \n" +
                "    &#1B4FFFV&#1B5BFFe&#1B66FFr&#1B72FFa&#1B7EFFx&#1B8AFFP&#1B95FFv&#1BA1FFP\n" +
                "    &#FF3333&lSunucu Bakımda\n" +
                "    \n" +
                "    Discord: discord.gg/veraxpvp\n" +
                "    &8__________________________________________________\n" +
                "\n" +
                "# ==============================================================================\n" +
                "auto-restart:\n" +
                "  bossbar-text: \"Custom Restart\"\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        // Result should contain custom VeraxPvP
        assertTrue(result.contains("discord.gg/veraxpvp"));
        // Result should NOT contain template discord.gg/yourdiscord
        assertFalse(result.contains("discord.gg/yourdiscord"));
        // auto-restart section should be intact
        assertTrue(result.contains("bossbar-text: \"Custom Restart\""));
    }

    @Test
    @DisplayName("Corrupted multiline block scalar with repeated blocks is auto-sanitized")
    void testCorruptedBlockScalarSanitization() throws Exception {
        String template = "maintenance:\n" +
                "  kick-screen: |\n" +
                "    &8__________________________________________________\n" +
                "    \n" +
                "    Template\n" +
                "    \n" +
                "    &8__________________________________________________\n";

        String divider = "&8__________________________________________________";
        String disk = "maintenance:\n" +
                "  kick-screen: |\n" +
                "    " + divider + "\n" +
                "    \n" +
                "    Custom PvP\n" +
                "    \n" +
                "    " + divider + "\n" +
                "    \n" +
                "    Duplicate Template 1\n" +
                "    \n" +
                "    " + divider + "\n" +
                "    \n" +
                "    Duplicate Template 2\n" +
                "    \n" +
                "    " + divider + "\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        assertTrue(result.contains("Custom PvP"));
        assertFalse(result.contains("Duplicate Template"));
    }

    @Test
    @DisplayName("Migrates obsolete message keys and ignores deprecated keys")
    void testMessageMigrationAndIgnoredKeys() throws Exception {
        String template = "messages:\n" +
                "  trade-too-short: \"Default too short %min%\"\n" +
                "  trade-too-long: \"Default too long %max%\"\n" +
                "  report-cooldown-warning: \"Default cooldown %remaining%\"\n";

        String disk = "messages:\n" +
                "  trade-min-length-msg: \"Custom too short <min>\"\n" +
                "  trade-max-length-msg: \"Custom too long <max>\"\n" +
                "  report-cooldown-msg: \"Custom cooldown <time>\"\n" +
                "  word-actions-already-open: \"Old word action msg\"\n" +
                "  report-accepted: \"Old report accepted\"\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        for (String k : diskYaml.getKeys(true)) {
            if (!diskYaml.isConfigurationSection(k) && !jarYaml.contains(k)) {
                obsolete.put(k, diskYaml.get(k));
            }
        }

        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        assertTrue(result.contains("Custom too short <min>"));
        assertTrue(result.contains("Custom too long <max>"));
        assertTrue(result.contains("Custom cooldown <time>"));
        assertTrue(consumed.contains("messages.trade-min-length-msg"));
        assertTrue(consumed.contains("messages.trade-max-length-msg"));
        assertTrue(consumed.contains("messages.report-cooldown-msg"));
        assertFalse(consumed.contains("messages.word-actions-already-open"));
    }

    @Test
    @DisplayName("Cleans up obsolete keys even when target key already exists on disk")
    void testObsoleteKeysCleanedEvenIfTargetExistsOnDisk() throws Exception {
        String template = "messages:\n" +
                "  trade-too-short: \"Default too short %min%\"\n" +
                "  trade-too-long: \"Default too long %max%\"\n" +
                "  report-cooldown-warning: \"Default cooldown %remaining%\"\n";

        String disk = "messages:\n" +
                "  trade-too-short: \"Existing too short %min%\"\n" +
                "  trade-too-long: \"Existing too long %max%\"\n" +
                "  report-cooldown-warning: \"Existing cooldown %remaining%\"\n" +
                "messages.report-reporter-accepted: \"Your report has been approved\"\n" +
                "messages.report-reporter-rejected: \"Your report has been rejected\"\n" +
                "messages.report-cooldown-msg: \"Cooldown <time>\"\n" +
                "messages.trade-min-length-msg: \"Too short <min>\"\n" +
                "messages.trade-max-length-msg: \"Too long <max>\"\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        Map<String, Object> obsolete = new LinkedHashMap<>();
        for (String k : diskYaml.getKeys(true)) {
            if (!diskYaml.isConfigurationSection(k) && !jarYaml.contains(k)) {
                obsolete.put(k, diskYaml.get(k));
            }
        }
        assertEquals(5, obsolete.size());

        Set<String> consumed = new HashSet<>();
        List<String> jarLines = Arrays.asList(template.split("\n"));
        String result = ConfigUpdater.synchronizeContent(jarLines, jarYaml, diskYaml, obsolete, consumed);

        Map<String, Object> unhandled = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : obsolete.entrySet()) {
            if (!consumed.contains(entry.getKey()) && !ConfigUpdater.isIgnoredObsoleteKey(entry.getKey())) {
                unhandled.put(entry.getKey(), entry.getValue());
            }
        }

        assertTrue(unhandled.isEmpty(), "All 5 legacy keys should be consumed or ignored: " + unhandled.keySet());
    }

    @Test
    @DisplayName("Auto-restart times scalar and list formats are preserved and normalized to HH:mm")
    void testAutoRestartTimesPreservationAndNormalization() throws Exception {
        // Test normalizeTimeString
        assertEquals("19:55", ConfigUpdater.normalizeTimeString("1195")); // YAML sexagesimal int (19 * 60 + 55)
        assertEquals("19:55", ConfigUpdater.normalizeTimeString("19.55")); // dot format
        assertEquals("19:55", ConfigUpdater.normalizeTimeString("19:55")); // standard format
        assertEquals("04:00", ConfigUpdater.normalizeTimeString("240"));  // 4 * 60

        String template = "auto-restart:\n" +
                "  enabled: true\n" +
                "  times:\n" +
                "    - \"04:00\"\n" +
                "    - \"16:00\"\n";
        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        // Case 1: User writes times: 19:55 (unquoted scalar)
        YamlConfiguration cfg1 = new YamlConfiguration();
        cfg1.loadFromString("auto-restart:\n  times: 19:55\n");
        String sync1 = ConfigUpdater.synchronizeContent(Arrays.asList(template.split("\n")), jarYaml, cfg1, Collections.emptyMap(), new HashSet<>());
        assertTrue(sync1.contains("- \"19:55\""), "Case 1 should preserve and normalize scalar int 19:55 to list with \"19:55\":\n" + sync1);

        // Case 2: User writes times: - 19:55 (unquoted list)
        YamlConfiguration cfg2 = new YamlConfiguration();
        cfg2.loadFromString("auto-restart:\n  times:\n    - 19:55\n");
        String sync2 = ConfigUpdater.synchronizeContent(Arrays.asList(template.split("\n")), jarYaml, cfg2, Collections.emptyMap(), new HashSet<>());
        assertTrue(sync2.contains("- \"19:55\""), "Case 2 should preserve and normalize unquoted list item 19:55 to \"19:55\":\n" + sync2);

        // Case 3: User writes times: "19:55" (quoted scalar)
        YamlConfiguration cfg3 = new YamlConfiguration();
        cfg3.loadFromString("auto-restart:\n  times: \"19:55\"\n");
        String sync3 = ConfigUpdater.synchronizeContent(Arrays.asList(template.split("\n")), jarYaml, cfg3, Collections.emptyMap(), new HashSet<>());
        assertTrue(sync3.contains("- \"19:55\""), "Case 3 should preserve quoted scalar \"19:55\" into list with \"19:55\":\n" + sync3);

        // Case 4: User writes times: - 19.55 (dot notation)
        YamlConfiguration cfg4 = new YamlConfiguration();
        cfg4.loadFromString("auto-restart:\n  times:\n    - 19.55\n");
        String sync4 = ConfigUpdater.synchronizeContent(Arrays.asList(template.split("\n")), jarYaml, cfg4, Collections.emptyMap(), new HashSet<>());
        assertTrue(sync4.contains("- \"19:55\""), "Case 4 should normalize dot notation 19.55 to \"19:55\":\n" + sync4);
    }

    @Test
    @DisplayName("Automatically upgrades old default language color codes while preserving custom user wording")
    void testAutoUpgradeLanguageColorCodes() throws Exception {
        String template = "messages:\n" +
                "  pv-opened: \"%prefix% &7Vault &a#%vault_no% &7opened.\"\n" +
                "  chat-closed-error: \"%prefix% &7Chat is currently &clocked!\"\n" +
                "  verification-title: \"&#1B4FFFVerification\"\n" +
                "  custom-edited: \"%prefix% &7Default message\"\n" +
                "trade-visuals:\n" +
                "  trade-lore:\n" +
                "    - \"&8\"\n" +
                "    - \"&#1B4FFFMessage: &#1BA1FF%message%\"\n" +
                "    - \"\"\n" +
                "    - \"&7Time Left: &#1BA1FF%remaining_time%\"\n";

        String disk = "messages:\n" +
                "  pv-opened: \"%prefix% &fVault &a#%vault_no% &fOpened.\"\n" +
                "  chat-closed-error: \"%prefix% &cChat is currently locked!\"\n" +
                "  verification-title: \"&#1B4FFF&lVERIFICATION REQUIRED\"\n" +
                "  custom-edited: \"%prefix% &eMy completely custom server message\"\n" +
                "trade-visuals:\n" +
                "  trade-lore:\n" +
                "    - \"&8\"\n" +
                "    - \"&#1B4FFFMessage: &#1BA1FF%message%\"\n" +
                "    - \"\"\n" +
                "    - \"&fTime Left: &#1BA1FF%remaining_time%\"\n";

        YamlConfiguration jarYaml = new YamlConfiguration();
        jarYaml.loadFromString(template);

        YamlConfiguration diskYaml = new YamlConfiguration();
        diskYaml.loadFromString(disk);

        String synced = ConfigUpdater.synchronizeContent(
                Arrays.asList(template.split("\n")), jarYaml, diskYaml, Collections.emptyMap(), new HashSet<>());

        assertTrue(synced.contains("pv-opened: \"%prefix% &7Vault &a#%vault_no% &7opened.\""));
        assertTrue(synced.contains("chat-closed-error: \"%prefix% &7Chat is currently &clocked!\""));
        assertTrue(synced.contains("verification-title: \"&#1B4FFFVerification\""));
        assertTrue(synced.contains("- \"&7Time Left: &#1BA1FF%remaining_time%\""));
        assertTrue(synced.contains("custom-edited: \"%prefix% &eMy completely custom server message\""));
    }
}




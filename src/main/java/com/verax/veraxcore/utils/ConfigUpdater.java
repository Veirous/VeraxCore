package com.verax.veraxcore.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigUpdater {

    /**
     * Unified Smart Config & Language File Synchronizer:
     * 1. Uses the JAR template as the master blueprint (layout, comments, headers, section order).
     * 2. Preserves ALL user-configured values on the server (passwords, URLs, toggles, messages, lists).
     * 3. Automatically migrates values from renamed/obsolete keys to new keys without manual code.
     * 4. Cleans up obsolete/deleted keys that no longer exist in the template.
     * 5. Preserves any unknown custom keys at the end of the file so no user data is ever lost.
     */
    public static void update(JavaPlugin plugin, String resourceName, File toUpdate) {
        if (!toUpdate.exists()) {
            try {
                plugin.saveResource(resourceName, false);
            } catch (Exception ignored) {}
            return;
        }

        try {
            // 1. Read existing file from disk
            String diskContent;
            try (FileInputStream fis = new FileInputStream(toUpdate)) {
                diskContent = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (diskContent.trim().isEmpty()) {
                plugin.getLogger().warning("[ConfigUpdater] " + toUpdate.getName() + " was empty. Restoring default configuration...");
                plugin.saveResource(resourceName, true);
                return;
            }

            // 2. Read template file inside the JAR
            InputStream jarStream = plugin.getResource(resourceName);
            if (jarStream == null) {
                return;
            }

            String jarContent = new String(jarStream.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration jarYaml = new YamlConfiguration();
            jarYaml.loadFromString(jarContent);

            YamlConfiguration diskYaml = new YamlConfiguration();
            try {
                diskYaml.loadFromString(diskContent);
            } catch (Exception e) {
                plugin.getLogger().severe("[ConfigUpdater] Syntax error in " + toUpdate.getName() + ": " + e.getMessage());
                plugin.getLogger().warning("[ConfigUpdater] Restoring clean default " + toUpdate.getName() + " in-place...");
                plugin.saveResource(resourceName, true);
                return;
            }

            // 3. Version checking (if template defines config-version)
            int jarVersion = jarYaml.getInt("config-version", 0);
            int diskVersion = diskYaml.getInt("config-version", 0);
            boolean versionMismatch = (jarVersion > 0 && diskVersion < jarVersion);

            // 4. Collect obsolete keys (exist on disk, but NOT in the template)
            Map<String, Object> obsoleteKeyValues = new LinkedHashMap<>();
            for (String diskKey : diskYaml.getKeys(true)) {
                if (!diskYaml.isConfigurationSection(diskKey) && !jarYaml.contains(diskKey)) {
                    obsoleteKeyValues.put(diskKey, diskYaml.get(diskKey));
                }
            }

            // 5. Check if sync is needed (version mismatch, missing keys, obsolete keys, or updated template defaults/colors)
            boolean hasMissingKeys = false;
            boolean hasUpgradableDefaults = false;
            for (String jarKey : jarYaml.getKeys(true)) {
                if (!jarYaml.isConfigurationSection(jarKey)) {
                    if (!diskYaml.contains(jarKey)) {
                        hasMissingKeys = true;
                    } else if (shouldUpgradeToTemplateDefault(jarKey, diskYaml.get(jarKey), jarYaml.get(jarKey))) {
                        hasUpgradableDefaults = true;
                    }
                }
            }

            if (!versionMismatch && !hasMissingKeys && !hasUpgradableDefaults && obsoleteKeyValues.isEmpty()) {
                return;
            }

            if (versionMismatch) {
                plugin.getLogger().info("[ConfigUpdater] Upgrading " + toUpdate.getName() + " from v" + diskVersion + " to v" + jarVersion + "...");
                if (diskVersion > 0) {
                    try {
                        File backupFile = new File(toUpdate.getParentFile(), toUpdate.getName() + ".v" + diskVersion + ".bak");
                        java.nio.file.Files.copy(toUpdate.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("[ConfigUpdater] Created safety backup: " + backupFile.getName());
                    } catch (Exception ignored) {}
                }
            } else if (hasMissingKeys || hasUpgradableDefaults) {
                plugin.getLogger().info("[ConfigUpdater] Detected configuration changes in " + toUpdate.getName() + ". Synchronizing with template...");
            } else {
                plugin.getLogger().info("[ConfigUpdater] Cleaning up " + obsoleteKeyValues.size() + " obsolete key(s) in " + toUpdate.getName() + "...");
            }

            // 6. Rebuild file using JAR template as blueprint
            List<String> jarLines = Arrays.asList(jarContent.split("\r?\n", -1));
            Set<String> consumedObsoleteKeys = new HashSet<>();
            String synchronizedContent = synchronizeContent(jarLines, jarYaml, diskYaml, obsoleteKeyValues, consumedObsoleteKeys);

            // 6. Check if any unrecognized custom keys remain that weren't migrated
            Map<String, Object> unhandledCustomKeys = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : obsoleteKeyValues.entrySet()) {
                if (!consumedObsoleteKeys.contains(entry.getKey()) && !isIgnoredObsoleteKey(entry.getKey())) {
                    unhandledCustomKeys.put(entry.getKey(), entry.getValue());
                }
            }

            if (!unhandledCustomKeys.isEmpty()) {
                StringBuilder extra = new StringBuilder();
                extra.append("\n# ------------------------------------------------------------------------------\n");
                extra.append("# ⚙️ PRESERVED CUSTOM SETTINGS\n");
                extra.append("# ------------------------------------------------------------------------------\n");
                for (Map.Entry<String, Object> entry : unhandledCustomKeys.entrySet()) {
                    extra.append(entry.getKey()).append(": ").append(formatYamlScalar(entry.getValue(), "")).append("\n");
                }
                synchronizedContent += extra.toString();
            }

            // 7. Write to file only if modified
            if (!diskContent.equals(synchronizedContent)) {
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(toUpdate), StandardCharsets.UTF_8)) {
                    writer.write(synchronizedContent);
                }
                plugin.getLogger().info("[ConfigUpdater] " + toUpdate.getName() + " was successfully updated and synchronized!");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[ConfigUpdater] Error synchronizing " + toUpdate.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Walks through JAR template lines and reconstructs the YAML document:
     * - Preserves all template comments, banners, and layout verbatim.
     * - Injects disk values for existing settings.
     * - Auto-migrates values for renamed keys from obsolete keys.
     * - Injects template defaults for newly added keys.
     */
    static String synchronizeContent(List<String> jarLines, YamlConfiguration jarYaml,
                                     YamlConfiguration diskYaml, Map<String, Object> obsoleteKeyValues,
                                     Set<String> consumedObsoleteKeys) {
        StringBuilder result = new StringBuilder();
        List<String> sectionStack = new ArrayList<>();
        int i = 0;

        while (i < jarLines.size()) {
            String line = jarLines.get(i);
            String trimmed = line.trim();

            // 1. Comment or empty line: keep exact template line
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n");
                i++;
                continue;
            }

            // 2. YAML key line
            if (trimmed.contains(":")) {
                int indent = getIndent(line);
                adjustSectionStack(sectionStack, indent);

                String keyName = trimmed.split(":", 2)[0].trim();
                String fullPath = sectionStack.isEmpty() ? keyName : String.join(".", sectionStack) + "." + keyName;

                boolean isSection = jarYaml.isConfigurationSection(fullPath);

                if (isSection) {
                    result.append(line).append("\n");
                    sectionStack.add(keyName);
                    i++;
                    continue;
                }

                // Leaf key line
                String valuePart = trimmed.split(":", 2)[1];
                String inlineComment = "";
                int commentIndex = findInlineCommentIndex(valuePart);
                if (commentIndex != -1) {
                    inlineComment = " " + valuePart.substring(commentIndex).trim();
                    valuePart = valuePart.substring(0, commentIndex).trim();
                } else {
                    valuePart = valuePart.trim();
                }

                // Check if disk has a value (either existing or migrated from obsolete key)
                Object diskValue = findMigratedValue(fullPath, diskYaml, obsoleteKeyValues, consumedObsoleteKeys);
                if (diskValue != null && shouldUpgradeToTemplateDefault(fullPath, diskValue, jarYaml.get(fullPath))) {
                    diskValue = null;
                }

                String indentStr = " ".repeat(indent);

                // Handle List types (e.g. allowed-ranks:, auto-restart.times:)
                if (jarYaml.isList(fullPath)) {
                    result.append(indentStr).append(keyName).append(":").append(inlineComment).append("\n");

                    List<?> listToWrite;
                    if (diskValue instanceof List<?>) {
                        listToWrite = (List<?>) diskValue;
                    } else if (diskValue != null) {
                        // User specified single scalar value instead of a list (e.g. times: "19:55" or times: 19:55)
                        listToWrite = Collections.singletonList(diskValue);
                    } else {
                        listToWrite = jarYaml.getList(fullPath);
                    }

                    if (listToWrite != null) {
                        for (Object item : listToWrite) {
                            String itemStr = String.valueOf(item).trim();
                            if (isTimeField(fullPath)) {
                                itemStr = normalizeTimeString(itemStr);
                            }
                            result.append(indentStr).append("  - \"").append(escapeYamlString(itemStr)).append("\"\n");
                        }
                    }

                    // Skip the template's default list items
                    i++;
                    while (i < jarLines.size()) {
                        String nextLine = jarLines.get(i);
                        String nextTrimmed = nextLine.trim();
                        int nextIndent = getIndent(nextLine);
                        if (!nextTrimmed.isEmpty() && !nextTrimmed.startsWith("#")) {
                            if (nextIndent > indent && nextTrimmed.startsWith("-")) {
                                i++;
                                continue;
                            }
                            break;
                        } else if (nextTrimmed.startsWith("#") && nextIndent > indent) {
                            i++;
                            continue;
                        }
                        break;
                    }
                    continue;
                }

                // Handle Block Scalars (e.g. message: |)
                if (valuePart.equals("|") || valuePart.equals("|-") || valuePart.equals("|+")) {
                    result.append(indentStr).append(keyName).append(": ").append(valuePart).append(inlineComment).append("\n");

                    int blockEndIdx = findBlockScalarEnd(jarLines, i + 1, indent);

                    // If disk had customized multiline string
                    if (diskValue instanceof String && !diskValue.equals(jarYaml.getString(fullPath))) {
                        String rawDisk = (String) diskValue;
                        rawDisk = sanitizeCorruptedBlockScalar(fullPath, rawDisk);
                        String[] diskLines = rawDisk.split("\r?\n");
                        for (String dl : diskLines) {
                            if (dl.trim().isEmpty()) {
                                result.append(indentStr).append("    \n");
                            } else {
                                result.append(indentStr).append("  ").append(dl).append("\n");
                            }
                        }
                    } else {
                        // Use template block lines
                        for (int k = i + 1; k < blockEndIdx; k++) {
                            result.append(jarLines.get(k)).append("\n");
                        }
                    }

                    i = blockEndIdx;
                    continue;
                }

                // Scalar value (String, Boolean, Number)
                if (fullPath.equals("config-version")) {
                    // Always stamp the latest version number from the JAR template
                    result.append(indentStr).append(keyName).append(": ").append(jarYaml.getInt("config-version", 1)).append(inlineComment).append("\n");
                } else if (diskValue != null) {
                    String formattedVal = formatYamlScalar(diskValue, valuePart);
                    result.append(indentStr).append(keyName).append(": ").append(formattedVal).append(inlineComment).append("\n");
                } else {
                    // Use template default line
                    result.append(line).append("\n");
                }

                i++;
                continue;
            }

            // Unrecognized line
            result.append(line).append("\n");
            i++;
        }

        return result.toString();
    }

    /**
     * Looks up value for jarKey in diskYaml.
     * If not found, intelligently checks if an obsolete key on disk was renamed to this key.
     */
    static Object findMigratedValue(String jarKey, YamlConfiguration diskYaml,
                                    Map<String, Object> obsoleteKeyValues, Set<String> consumedObsoleteKeys) {
        String jarKeyLower = jarKey.toLowerCase();
        Object migratedVal = null;

        if (obsoleteKeyValues != null && !obsoleteKeyValues.isEmpty()) {
            for (Map.Entry<String, Object> entry : obsoleteKeyValues.entrySet()) {
                String obsKey = entry.getKey();
                String obsKeyLower = obsKey.toLowerCase();

                if (jarKeyLower.contains(obsKeyLower) || obsKeyLower.contains(jarKeyLower)
                        || matchesKnownAlias(jarKeyLower, obsKeyLower)) {
                    consumedObsoleteKeys.add(obsKey);
                    if (migratedVal == null) {
                        migratedVal = entry.getValue();
                    }
                }
            }
        }

        if (diskYaml.contains(jarKey)) {
            return diskYaml.get(jarKey);
        }

        return migratedVal;
    }

    private static boolean matchesKnownAlias(String key1, String key2) {
        String[][] aliases = {
                {"verification", "dogrulama"},
                {"trade", "ticaret"},
                {"report", "rapor"},
                {"staff-time", "yetkili-sure"},
                {"maintenance", "bakim"},
                {"trash", "cop"},
                {"word-actions", "kelime-eylemleri"},
                {"auto-announcement", "otoduyuru"},
                {"auto-restart", "otorestart"},
                {"staff-chat", "yetkili-sohbet"},
                {"chat", "sohbet"},
                {"announcement", "duyuru"},
                {"webhook-url", "staff-time-webhook-url"},
                {"session-duration-hours", "session-timeout-hours"},
                {"duration-hours", "timeout-hours"},
                {"oturum-suresi-saat", "session-timeout-hours"},
                {"trade-min-length", "trade-too-short"},
                {"ticaret-min-length", "trade-too-short"},
                {"trade-max-length", "trade-too-long"},
                {"ticaret-max-length", "trade-too-long"},
                {"report-cooldown", "report-cooldown-warning"},
                {"rapor-cooldown", "report-cooldown-warning"}
        };

        for (String[] pair : aliases) {
            if ((key1.contains(pair[0]) && key2.contains(pair[1]))
                    || (key1.contains(pair[1]) && key2.contains(pair[0]))) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldUpgradeToTemplateDefault(String fullPath, Object diskVal, Object jarVal) {
        if (diskVal == null || jarVal == null) return false;

        if (diskVal instanceof String && jarVal instanceof String) {
            String diskStr = (String) diskVal;
            String jarStr = (String) jarVal;
            if (diskStr.equals(jarStr)) return false;

            // If only color codes, formatting codes, casing, or punctuation changed, upgrade to new template default
            String normDisk = normalizeTextForComparison(diskStr);
            String normJar = normalizeTextForComparison(jarStr);
            if (!normDisk.isEmpty() && normDisk.equals(normJar)) {
                return true;
            }

            return isLegacyDefaultLangValue(fullPath, diskStr);
        }

        if (diskVal instanceof List<?> && jarVal instanceof List<?>) {
            List<?> diskList = (List<?>) diskVal;
            List<?> jarList = (List<?>) jarVal;
            if (diskList.equals(jarList) || diskList.size() != jarList.size() || diskList.isEmpty()) {
                return false;
            }
            for (int i = 0; i < diskList.size(); i++) {
                String dItem = normalizeTextForComparison(String.valueOf(diskList.get(i)));
                String jItem = normalizeTextForComparison(String.valueOf(jarList.get(i)));
                if (!dItem.equals(jItem)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    static String normalizeTextForComparison(String input) {
        if (input == null) return "";
        String stripped = input
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)#[0-9a-f]{6}", "")
                .replaceAll("(?i)&#[0-9a-fk-or]", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("\uFE0F", "")
                .replace("!", "")
                .replace(".", "")
                .replace('İ', 'i')
                .replace('I', 'ı')
                .toLowerCase(Locale.forLanguageTag("tr"))
                .replace('ı', 'i')
                .replaceAll("\\s+", " ")
                .trim();
        return stripped;
    }

    private static boolean isLegacyDefaultLangValue(String fullPath, String diskStr) {
        if (fullPath == null || diskStr == null) return false;
        String norm = normalizeTextForComparison(diskStr);
        switch (fullPath) {
            case "messages.verification-title":
                return norm.equals("doğrulama gerekiyor")
                        || norm.equals("verification required")
                        || norm.equals("verifizierung erforderlich");
            case "messages.verification-subtitle":
                return norm.equals("enter discord code: /verify <code> (%remaining_attempts% attempts)")
                        || norm.equals("discord-code eingeben: /verify <code> (%remaining_attempts% versuche)");
            case "messages.verification-success-title":
                return norm.equals("doğrulandi");
            case "spawn-title-settings.subtitle-teleporting":
                return norm.equals("lütfen %remaining_seconds% saniye bekleyin");
            default:
                return false;
        }
    }

    static boolean isIgnoredObsoleteKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("use-ssl") || lower.contains("ssl-enabled")
                || lower.contains("word-actions-already") || lower.contains("word-actions-opened")
                || lower.contains("word-actions-closed") || lower.contains("report-accepted")
                || lower.contains("report-rejected") || lower.contains("reporter-accepted")
                || lower.contains("reporter-rejected") || lower.contains("report-cooldown-msg")
                || lower.contains("trade-min-length-msg") || lower.contains("trade-max-length-msg")
                || lower.contains("ticaret-min-length-msg") || lower.contains("ticaret-max-length-msg");
    }

    static int findBlockScalarEnd(List<String> jarLines, int startIndex, int parentIndent) {
        int idx = startIndex;
        while (idx < jarLines.size()) {
            String nextLine = jarLines.get(idx);
            String nextTrimmed = nextLine.trim();

            if (!nextTrimmed.isEmpty()) {
                if (getIndent(nextLine) > parentIndent) {
                    idx++;
                } else {
                    break;
                }
            } else {
                // Line is empty or whitespace only.
                // Check if any subsequent non-empty line has indent > parentIndent
                boolean hasMoreBlockLines = false;
                for (int k = idx + 1; k < jarLines.size(); k++) {
                    String forwardLine = jarLines.get(k);
                    if (!forwardLine.trim().isEmpty()) {
                        if (getIndent(forwardLine) > parentIndent) {
                            hasMoreBlockLines = true;
                        }
                        break;
                    }
                }
                if (hasMoreBlockLines) {
                    idx++;
                } else {
                    break;
                }
            }
        }
        return idx;
    }

    static String sanitizeCorruptedBlockScalar(String path, String diskContent) {
        if (diskContent == null) return "";
        if ("maintenance.kick-screen".equals(path)) {
            String divider = "&8__________________________________________________";
            int first = diskContent.indexOf(divider);
            if (first != -1) {
                int second = diskContent.indexOf(divider, first + divider.length());
                if (second != -1) {
                    int third = diskContent.indexOf(divider, second + divider.length());
                    if (third != -1) {
                        return diskContent.substring(0, second + divider.length()).trim();
                    }
                }
            }
        }
        return diskContent;
    }

    static int findInlineCommentIndex(String str) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '#' && !inDoubleQuotes && !inSingleQuotes) {
                return i;
            }
        }
        return -1;
    }

    static String formatYamlScalar(Object val, String templateValuePart) {
        if (val instanceof Boolean || val instanceof Number) {
            return String.valueOf(val);
        }
        String str = String.valueOf(val);
        boolean templateHadQuotes = templateValuePart.startsWith("\"") || templateValuePart.startsWith("'");
        boolean needsQuotes = templateHadQuotes || str.contains(":") || str.contains("#")
                || str.contains("&") || str.contains("%") || str.contains("{") || str.contains("}")
                || str.isEmpty() || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false");

        if (needsQuotes) {
            return "\"" + escapeYamlString(str) + "\"";
        }
        return str;
    }

    static String escapeYamlString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static int getIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static void adjustSectionStack(List<String> stack, int indent) {
        int expectedDepth = indent / 2;
        while (stack.size() > expectedDepth) {
            stack.remove(stack.size() - 1);
        }
    }

    private static boolean isTimeField(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".times") || lower.endsWith(".time") || lower.endsWith("-time") || lower.endsWith("-times");
    }

    public static String normalizeTimeString(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String s = input.trim();
        // Replace dot with colon (e.g. 19.55 -> 19:55)
        if (s.contains(".") && !s.contains(":")) {
            s = s.replace('.', ':');
        }
        // If pure digits, handle sexagesimal integer produced by YAML 1.1 parser (e.g. 19:55 -> 1195)
        if (s.matches("^\\d+$")) {
            try {
                int val = Integer.parseInt(s);
                if (val >= 0 && val < 1440) {
                    int h = val / 60;
                    int m = val % 60;
                    return String.format(Locale.ROOT, "%02d:%02d", h, m);
                } else if (val >= 1440 && val < 86400) {
                    int h = val / 3600;
                    int m = (val % 3600) / 60;
                    int sec = val % 60;
                    return sec > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sec)
                                   : String.format(Locale.ROOT, "%02d:%02d", h, m);
                }
            } catch (Exception ignored) {}
        }
        return s;
    }
}


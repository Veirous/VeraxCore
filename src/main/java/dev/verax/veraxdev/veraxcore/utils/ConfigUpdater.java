package dev.verax.veraxdev.veraxcore.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Safe, In-Place Config & Language File Synchronizer:
 * 1. Zero backup files: Never creates .bak, .v1.bak, .old, or .backup files.
 * 2. Compares user's disk configuration with the plugin's bundled JAR template.
 * 3. 100% preservation of user modifications (custom values, webhooks, booleans, lists, block scalars).
 * 4. User custom sections and entries stay in their exact sections with proper YAML indentation.
 * 5. Newly added plugin settings/keys are injected in their exact template position with full comments and no line shifts.
 * 6. Protects user configuration: YAML syntax errors on disk will never wipe user files.
 */
public class ConfigUpdater {

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
                plugin.getLogger().warning("[ConfigUpdater] Skipping synchronization to protect your customized settings. Please fix the YAML syntax error in " + toUpdate.getName() + ".");
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

            // 5. Check if sync is needed (version mismatch, missing keys, obsolete migrations, or language defaults)
            boolean isLang = (resourceName != null && resourceName.startsWith("lang/")) || jarYaml.contains("messages") || diskYaml.contains("messages");
            boolean hasMissingKeys = false;
            boolean hasUpgradableDefaults = false;
            for (String jarKey : jarYaml.getKeys(true)) {
                if (!jarYaml.isConfigurationSection(jarKey)) {
                    if (!diskYaml.contains(jarKey)) {
                        hasMissingKeys = true;
                    } else if (isLang && shouldUpgradeToTemplateDefault(jarKey, diskYaml.get(jarKey), jarYaml.get(jarKey))) {
                        hasUpgradableDefaults = true;
                    }
                }
            }

            if (!versionMismatch && !hasMissingKeys && !hasUpgradableDefaults && obsoleteKeyValues.isEmpty()) {
                return;
            }

            if (versionMismatch) {
                plugin.getLogger().info("[ConfigUpdater] Synchronizing " + toUpdate.getName() + " (v" + diskVersion + " -> v" + jarVersion + ")...");
            } else if (hasMissingKeys || hasUpgradableDefaults) {
                plugin.getLogger().info("[ConfigUpdater] Detected configuration changes in " + toUpdate.getName() + ". Synchronizing with template...");
            } else {
                plugin.getLogger().info("[ConfigUpdater] Cleaning up " + obsoleteKeyValues.size() + " obsolete key(s) in " + toUpdate.getName() + "...");
            }

            // 6. Rebuild file using JAR template as blueprint and preserving all custom sections in-place
            List<String> jarLines = Arrays.asList(jarContent.split("\r?\n", -1));
            Set<String> consumedObsoleteKeys = new HashSet<>();
            String synchronizedContent = synchronizeContent(jarLines, jarYaml, diskYaml, obsoleteKeyValues, consumedObsoleteKeys, resourceName);

            // 7. Write to file directly in-place without creating any .bak / backup files
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

    static String synchronizeContent(List<String> jarLines, YamlConfiguration jarYaml,
                                     YamlConfiguration diskYaml, Map<String, Object> obsoleteKeyValues,
                                     Set<String> consumedObsoleteKeys) {
        return synchronizeContent(jarLines, jarYaml, diskYaml, obsoleteKeyValues, consumedObsoleteKeys, "config.yml");
    }

    static String synchronizeContent(List<String> jarLines, YamlConfiguration jarYaml,
                                     YamlConfiguration diskYaml, Map<String, Object> obsoleteKeyValues,
                                     Set<String> consumedObsoleteKeys, String resourceName) {
        StringBuilder result = new StringBuilder();
        Deque<SectionFrame> sectionStack = new ArrayDeque<>();
        Set<String> rootWrittenKeys = new HashSet<>();
        boolean isLang = (resourceName != null && resourceName.startsWith("lang/")) || jarYaml.contains("messages") || diskYaml.contains("messages");

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

            ParsedKey parsed = parseKeyLine(line);
            if (parsed == null) {
                result.append(line).append("\n");
                i++;
                continue;
            }

            int indent = parsed.indent;
            String keyName = parsed.key;

            // Pop any sections that are at the same or deeper indent level
            while (!sectionStack.isEmpty() && sectionStack.peek().indent >= indent) {
                SectionFrame popped = sectionStack.pop();
                flushCustomChildren(result, popped, diskYaml, jarYaml, consumedObsoleteKeys);
            }

            String fullPath = sectionStack.isEmpty() ? keyName : sectionStack.peek().fullPath + "." + keyName;

            if (!sectionStack.isEmpty()) {
                sectionStack.peek().writtenChildren.add(keyName);
            } else {
                rootWrittenKeys.add(keyName);
            }

            boolean isSection = jarYaml.isConfigurationSection(fullPath);
            if (isSection) {
                result.append(line).append("\n");
                sectionStack.push(new SectionFrame(keyName, indent, fullPath));
                i++;
                continue;
            }

            // Leaf key line
            Object diskValue = findMigratedValue(fullPath, diskYaml, obsoleteKeyValues, consumedObsoleteKeys);
            if (isLang && diskValue != null && shouldUpgradeToTemplateDefault(fullPath, diskValue, jarYaml.get(fullPath))) {
                diskValue = null;
            }

            String indentStr = " ".repeat(indent);

            // Handle List types (e.g. allowed-ranks:, auto-restart.times:)
            if (jarYaml.isList(fullPath)) {
                result.append(indentStr).append(keyName).append(":").append(parsed.inlineComment).append("\n");

                List<?> listToWrite;
                if (diskValue instanceof List<?>) {
                    listToWrite = (List<?>) diskValue;
                } else if (diskValue != null) {
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

                i = skipTemplateListItems(jarLines, i + 1, indent);
                continue;
            }

            // Handle Block Scalars (e.g. message: |)
            if (parsed.valuePart.equals("|") || parsed.valuePart.equals("|-") || parsed.valuePart.equals("|+")) {
                result.append(indentStr).append(keyName).append(": ").append(parsed.valuePart).append(parsed.inlineComment).append("\n");

                int blockEndIdx = findBlockScalarEnd(jarLines, i + 1, indent);

                if (diskValue instanceof String && !diskValue.equals(jarYaml.getString(fullPath))) {
                    String rawDisk = sanitizeCorruptedBlockScalar(fullPath, (String) diskValue);
                    String[] diskLines = rawDisk.split("\r?\n");
                    for (String dl : diskLines) {
                        if (dl.isEmpty()) {
                            result.append("\n");
                        } else {
                            result.append(indentStr).append("  ").append(dl).append("\n");
                        }
                    }
                } else {
                    for (int k = i + 1; k < blockEndIdx; k++) {
                        result.append(jarLines.get(k)).append("\n");
                    }
                }

                i = blockEndIdx;
                continue;
            }

            // Scalar value (String, Boolean, Number)
            if (fullPath.equals("config-version")) {
                result.append(indentStr).append(keyName).append(": ").append(jarYaml.getInt("config-version", 1)).append(parsed.inlineComment).append("\n");
            } else if (diskValue != null) {
                String formattedVal = formatYamlScalar(diskValue, parsed.valuePart);
                result.append(indentStr).append(keyName).append(": ").append(formattedVal).append(parsed.inlineComment).append("\n");
            } else {
                // Key missing from disk -> use template default line verbatim
                result.append(line).append("\n");
            }

            i++;
        }

        // Flush remaining sections in stack
        while (!sectionStack.isEmpty()) {
            SectionFrame popped = sectionStack.pop();
            flushCustomChildren(result, popped, diskYaml, jarYaml, consumedObsoleteKeys);
        }

        // Check if disk has any top-level sections/keys not in template and not yet written
        for (String rootKey : diskYaml.getKeys(false)) {
            if (!rootWrittenKeys.contains(rootKey) && !jarYaml.contains(rootKey)) {
                if (!isIgnoredObsoleteKey(rootKey) && !consumedObsoleteKeys.contains(rootKey)) {
                    serializeCustomNode(result, diskYaml, rootKey, 0, consumedObsoleteKeys);
                }
            }
        }

        return result.toString();
    }

    private static void flushCustomChildren(StringBuilder result, SectionFrame frame,
                                            YamlConfiguration diskYaml, YamlConfiguration jarYaml,
                                            Set<String> consumedObsoleteKeys) {
        ConfigurationSection diskSection = diskYaml.getConfigurationSection(frame.fullPath);
        if (diskSection == null) return;

        ConfigurationSection jarSection = jarYaml.getConfigurationSection(frame.fullPath);
        Set<String> jarChildKeys = jarSection != null ? jarSection.getKeys(false) : Collections.emptySet();

        for (String childKey : diskSection.getKeys(false)) {
            if (frame.writtenChildren.contains(childKey)) {
                continue;
            }
            if (jarChildKeys.contains(childKey)) {
                continue;
            }

            String fullChildPath = frame.fullPath + "." + childKey;
            if (isIgnoredObsoleteKey(fullChildPath) || consumedObsoleteKeys.contains(fullChildPath)) {
                continue;
            }

            int childIndent = frame.indent + 2;
            serializeCustomNode(result, diskSection, childKey, childIndent, consumedObsoleteKeys);
            frame.writtenChildren.add(childKey);
        }
    }

    private static void serializeCustomNode(StringBuilder result, ConfigurationSection parentSection,
                                            String key, int indent, Set<String> consumedObsoleteKeys) {
        String indentStr = " ".repeat(indent);
        String currentPath = parentSection.getCurrentPath();
        String fullPath = currentPath == null || currentPath.isEmpty() ? key : currentPath + "." + key;
        consumedObsoleteKeys.add(fullPath);

        if (parentSection.isConfigurationSection(key)) {
            ConfigurationSection sub = parentSection.getConfigurationSection(key);
            result.append(indentStr).append(key).append(":\n");
            if (sub != null) {
                for (String subKey : sub.getKeys(false)) {
                    serializeCustomNode(result, sub, subKey, indent + 2, consumedObsoleteKeys);
                }
            }
        } else if (parentSection.isList(key)) {
            result.append(indentStr).append(key).append(":\n");
            List<?> list = parentSection.getList(key);
            if (list != null) {
                for (Object item : list) {
                    result.append(indentStr).append("  - \"").append(escapeYamlString(String.valueOf(item))).append("\"\n");
                }
            }
        } else {
            Object val = parentSection.get(key);
            result.append(indentStr).append(key).append(": ").append(formatYamlScalar(val, "")).append("\n");
        }
    }

    static ParsedKey parseKeyLine(String line) {
        int indent = getIndent(line);
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
            return null;
        }

        int colonIdx = findKeyColonIndex(line);
        if (colonIdx == -1) {
            return null;
        }

        String keyPart = line.substring(indent, colonIdx).trim();
        if (keyPart.isEmpty()) {
            return null;
        }
        String unquotedKey = unquoteKey(keyPart);
        String afterColon = line.substring(colonIdx + 1);

        String inlineComment = "";
        String valuePart;
        int commentIdx = findInlineCommentIndex(afterColon);
        if (commentIdx != -1) {
            inlineComment = " " + afterColon.substring(commentIdx).trim();
            valuePart = afterColon.substring(0, commentIdx).trim();
        } else {
            valuePart = afterColon.trim();
        }

        return new ParsedKey(indent, unquotedKey, valuePart, inlineComment);
    }

    static int findKeyColonIndex(String line) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

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
            } else if (!inSingleQuotes && !inDoubleQuotes) {
                if (c == '#') {
                    return -1;
                }
                if (c == ':') {
                    if (i + 1 == line.length() || Character.isWhitespace(line.charAt(i + 1)) || line.charAt(i + 1) == '#') {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static String unquoteKey(String key) {
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    static Object findMigratedValue(String jarKey, YamlConfiguration diskYaml,
                                    Map<String, Object> obsoleteKeyValues, Set<String> consumedObsoleteKeys) {
        String jarKeyLower = jarKey.toLowerCase(Locale.ROOT);
        Object migratedVal = null;

        if (obsoleteKeyValues != null && !obsoleteKeyValues.isEmpty()) {
            for (Map.Entry<String, Object> entry : obsoleteKeyValues.entrySet()) {
                String obsKey = entry.getKey();
                String obsKeyLower = obsKey.toLowerCase(Locale.ROOT);

                if (matchesKnownAlias(jarKeyLower, obsKeyLower)) {
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
        return input
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)#[0-9a-f]{6}", "")
                .replaceAll("(?i)&#[0-9a-fk-or]", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("\uFE0F", "")
                .replace("!", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isLegacyDefaultLangValue(String fullPath, String diskStr) {
        if (fullPath == null || diskStr == null) return false;
        String norm = normalizeTextForComparison(diskStr);
        switch (fullPath) {
            case "messages.verification-title":
                return norm.equals("verification required")
                        || norm.equals("verifizierung erforderlich");
            case "messages.verification-subtitle":
                return norm.equals("enter discord code: /verify <code> (%remaining_attempts% attempts)")
                        || norm.equals("discord-code eingeben: /verify <code> (%remaining_attempts% versuche)");
            case "messages.verification-success-title":
                return norm.equals("verified");
            case "spawn-title-settings.subtitle-teleporting":
                return norm.equals("please wait %remaining_seconds% seconds");
            default:
                return false;
        }
    }

    static boolean isIgnoredObsoleteKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("use-ssl") || lower.contains("ssl-enabled")
                || lower.contains("word-actions-already") || lower.contains("word-actions-opened")
                || lower.contains("word-actions-closed") || lower.contains("report-accepted")
                || lower.contains("report-rejected") || lower.contains("reporter-accepted")
                || lower.contains("reporter-rejected") || lower.contains("report-cooldown-msg")
                || lower.contains("trade-min-length-msg") || lower.contains("trade-max-length-msg")
                || lower.contains("ticaret-min-length-msg") || lower.contains("ticaret-max-length-msg");
    }

    static int skipTemplateListItems(List<String> jarLines, int startIndex, int listIndent) {
        int idx = startIndex;
        while (idx < jarLines.size()) {
            String nextLine = jarLines.get(idx);
            String nextTrimmed = nextLine.trim();
            int nextIndent = getIndent(nextLine);

            if (nextTrimmed.isEmpty()) {
                boolean hasMoreListItems = false;
                for (int k = idx + 1; k < jarLines.size(); k++) {
                    String forwardLine = jarLines.get(k);
                    String forwardTrimmed = forwardLine.trim();
                    if (!forwardTrimmed.isEmpty()) {
                        if (getIndent(forwardLine) > listIndent && (forwardTrimmed.startsWith("-") || forwardTrimmed.startsWith("#"))) {
                            hasMoreListItems = true;
                        }
                        break;
                    }
                }
                if (hasMoreListItems) {
                    idx++;
                    continue;
                } else {
                    break;
                }
            }

            if (nextIndent > listIndent) {
                if (nextTrimmed.startsWith("-") || nextTrimmed.startsWith("#")) {
                    idx++;
                    continue;
                }
            }
            break;
        }
        return idx;
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
        boolean templateHadQuotes = templateValuePart != null &&
                (templateValuePart.startsWith("\"") || templateValuePart.startsWith("'"));
        boolean needsQuotes = templateHadQuotes || str.contains(":") || str.contains("#")
                || str.contains("&") || str.contains("%") || str.contains("{") || str.contains("}")
                || str.contains("[") || str.contains("]") || str.contains(",")
                || str.isEmpty() || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")
                || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("yes") || str.equalsIgnoreCase("no");

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

    private static boolean isTimeField(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".times") || lower.endsWith(".time") || lower.endsWith("-time") || lower.endsWith("-times");
    }

    public static String normalizeTimeString(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String s = input.trim();
        if (s.contains(".") && !s.contains(":")) {
            s = s.replace('.', ':');
        }
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

    static class ParsedKey {
        final int indent;
        final String key;
        final String valuePart;
        final String inlineComment;

        ParsedKey(int indent, String key, String valuePart, String inlineComment) {
            this.indent = indent;
            this.key = key;
            this.valuePart = valuePart;
            this.inlineComment = inlineComment;
        }
    }

    static class SectionFrame {
        final String key;
        final int indent;
        final String fullPath;
        final Set<String> writtenChildren = new HashSet<>();

        SectionFrame(String key, int indent, String fullPath) {
            this.key = key;
            this.indent = indent;
            this.fullPath = fullPath;
        }
    }
}

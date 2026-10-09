package com.verax.veraxcore.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigUpdater {

    /**
     * Smart and Safe Config & Language File Updater:
     * 1. Preserves existing user settings, custom values, and comments.
     * 2. Automatically restores missing or deleted keys with their comments (#) and proper YAML indentation.
     * 3. Creates no backup/extra files, performing all operations directly on the existing file.
     */
    public static void update(JavaPlugin plugin, String resourceName, File toUpdate) {
        if (!toUpdate.exists()) {
            try {
                plugin.saveResource(resourceName, false);
            } catch (Exception ignored) {}
            return;
        }

        try {
            // 1. Read file from disk
            String diskContent;
            try (FileInputStream fis = new FileInputStream(toUpdate)) {
                diskContent = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            // If file is empty or only whitespace, restore template directly
            if (diskContent.trim().isEmpty()) {
                plugin.getLogger().warning("[ConfigUpdater] " + toUpdate.getName() + " was empty. Restoring default configuration...");
                plugin.saveResource(resourceName, true);
                return;
            }

            // 2. Read template file inside the jar
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
                // If YAML syntax is invalid, warn and restore default template in-place
                plugin.getLogger().severe("[ConfigUpdater] Syntax error in " + toUpdate.getName() + ": " + e.getMessage());
                plugin.getLogger().warning("[ConfigUpdater] Restoring clean default " + toUpdate.getName() + " in-place...");
                plugin.saveResource(resourceName, true);
                return;
            }

            // 3. Detect missing or deleted keys
            Set<String> missingKeys = new LinkedHashSet<>();
            for (String key : jarYaml.getKeys(true)) {
                if (!diskYaml.contains(key)) {
                    missingKeys.add(key);
                }
            }

            if (missingKeys.isEmpty()) {
                return;
            }

            plugin.getLogger().info("[ConfigUpdater] " + missingKeys.size() + " missing or deleted key(s) detected in " + toUpdate.getName() + ". Restoring missing lines...");

            // 4. Analyze jar lines and insert missing keys with proper YAML hierarchy into disk lines
            List<String> jarLines = Arrays.asList(jarContent.split("\r?\n", -1));
            List<String> diskLines = new ArrayList<>(Arrays.asList(diskContent.split("\r?\n", -1)));

            boolean modified = syncMissingKeys(jarLines, diskLines, jarYaml, diskYaml, missingKeys);

            // 5. Save updated content in UTF-8 format
            if (modified) {
                String updatedContent = String.join("\n", diskLines);
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(toUpdate), StandardCharsets.UTF_8)) {
                    writer.write(updatedContent);
                }
                plugin.getLogger().info("[ConfigUpdater] " + toUpdate.getName() + " was successfully repaired and updated with missing lines!");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[ConfigUpdater] Error checking " + toUpdate.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Reads jar lines and appends missing key blocks (and their leading comments)
     * to diskLines with appropriate indentation and hierarchy.
     */
    private static boolean syncMissingKeys(List<String> jarLines, List<String> diskLines,
                                          YamlConfiguration jarYaml, YamlConfiguration diskYaml,
                                          Set<String> missingKeys) {
        boolean modified = false;
        List<String> sectionStack = new ArrayList<>();
        List<String> commentBuffer = new ArrayList<>();

        for (int i = 0; i < jarLines.size(); i++) {
            String line = jarLines.get(i);
            String trimmed = line.trim();

            // Buffer empty lines or comment lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                commentBuffer.add(line);
                continue;
            }

            // Is this a YAML key line? (e.g., "maintenance:", "  enabled: false", "prefix: ...")
            if (trimmed.contains(":")) {
                int indent = getIndent(line);
                String keyName = trimmed.split(":", 2)[0].trim();

                // Adjust section stack depth according to indentation (2 spaces per level)
                adjustSectionStack(sectionStack, indent);

                // Build full key path (e.g. "maintenance.bossbar.color")
                String fullPath = sectionStack.isEmpty() ? keyName : String.join(".", sectionStack) + "." + keyName;

                // Check if this key is a section
                boolean isSection = jarYaml.isConfigurationSection(fullPath);

                if (missingKeys.contains(fullPath)) {
                    // Collect this key and associated lines (lists, multiline blocks, etc.)
                    List<String> blockToInsert = new ArrayList<>(commentBuffer);
                    blockToInsert.add(line);

                    // Block scalar detection (| or >)
                    boolean isBlockScalar = trimmed.endsWith("|") || trimmed.endsWith("|-") || trimmed.endsWith("|+")
                            || trimmed.endsWith(">") || trimmed.endsWith(">-") || trimmed.endsWith(">+");

                    // If list (- item) or multiline value, collect remaining lines
                    while (i + 1 < jarLines.size()) {
                        String nextLine = jarLines.get(i + 1);
                        String nextTrimmed = nextLine.trim();
                        int nextIndent = getIndent(nextLine);

                        if (nextTrimmed.isEmpty()) {
                            // Empty lines: include if inside multiline block or following line is part of block
                            boolean stillInBlock = false;
                            for (int look = i + 2; look < jarLines.size(); look++) {
                                String lookTrimmed = jarLines.get(look).trim();
                                if (!lookTrimmed.isEmpty() && !lookTrimmed.startsWith("#")) {
                                    if (getIndent(jarLines.get(look)) > indent) {
                                        stillInBlock = true;
                                    }
                                    break;
                                }
                            }
                            if (stillInBlock || isBlockScalar) {
                                blockToInsert.add(nextLine);
                                i++;
                                continue;
                            } else {
                                break;
                            }
                        }

                        if (nextIndent > indent) {
                            if (isBlockScalar) {
                                blockToInsert.add(nextLine);
                                i++;
                            } else if (nextTrimmed.startsWith("-") || !nextTrimmed.contains(":")) {
                                blockToInsert.add(nextLine);
                                i++;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }

                    // Insert into diskLines
                    insertBlock(diskLines, sectionStack, blockToInsert);
                    missingKeys.remove(fullPath);
                    modified = true;
                }

                if (isSection) {
                    sectionStack.add(keyName);
                }
            }

            commentBuffer.clear();
        }

        return modified;
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

    /**
     * Inserts block into disk lines under parent section.
     */
    private static void insertBlock(List<String> diskLines, List<String> sectionStack, List<String> blockToInsert) {
        if (sectionStack.isEmpty()) {
            diskLines.addAll(blockToInsert);
            return;
        }

        int parentIndex = findParentSectionIndex(diskLines, sectionStack);
        if (parentIndex == -1) {
            diskLines.addAll(blockToInsert);
            return;
        }

        // Find end of section
        int parentIndent = (sectionStack.size() - 1) * 2;
        int insertIndex = parentIndex + 1;

        while (insertIndex < diskLines.size()) {
            String line = diskLines.get(insertIndex);
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && getIndent(line) <= parentIndent) {
                break;
            }
            insertIndex++;
        }

        diskLines.addAll(insertIndex, blockToInsert);
    }

    /**
     * Finds parent section line index matching sectionStack path in diskLines.
     */
    private static int findParentSectionIndex(List<String> diskLines, List<String> sectionStack) {
        int currentParentIndex = -1;
        int currentStart = 0;
        int currentEnd = diskLines.size();

        for (int level = 0; level < sectionStack.size(); level++) {
            String targetKey = sectionStack.get(level);
            int targetIndent = level * 2;
            int foundIndex = -1;

            for (int i = currentStart; i < currentEnd; i++) {
                String line = diskLines.get(i);
                String trimmed = line.trim();
                if (getIndent(line) == targetIndent && (trimmed.equals(targetKey + ":") || trimmed.startsWith(targetKey + ":"))) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex == -1) {
                return currentParentIndex;
            }

            currentParentIndex = foundIndex;
            currentStart = foundIndex + 1;
            currentEnd = diskLines.size();
            for (int j = currentStart; j < diskLines.size(); j++) {
                String line = diskLines.get(j);
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && getIndent(line) <= targetIndent) {
                    currentEnd = j;
                    break;
                }
            }
        }

        return currentParentIndex;
    }
}

package com.verax.veraxcore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paper 1.20.4 Adventure and MiniMessage utility class.
 * Handles both legacy color codes (&a, &#RRGGBB) and modern MiniMessage tags (<gradient>, <hover>, <click>, <rainbow>)
 * with full backwards compatibility.
 */
public final class AdventureUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    // &#RRGGBB format
    private static final Pattern HEX_AMPERSAND_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // #RRGGBB format (without & at word boundary or after whitespace)
    private static final Pattern HEX_HASH_PATTERN = Pattern.compile("(?<=\\s|^)#([A-Fa-f0-9]{6})");
    // Bukkit §x§r§r§g§g§b§b format
    private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile("§x(§[A-Fa-f0-9]){6}");

    private AdventureUtil() {}

    /**
     * Converts legacy color codes in the provided text to MiniMessage tags.
     */
    public static String convertLegacyToMiniMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String result = message;

        // 1. Convert Bukkit §x§r§r§g§g§b§b hex codes to <#RRGGBB>
        Matcher bukkitHexMatcher = BUKKIT_HEX_PATTERN.matcher(result);
        StringBuffer bukkitSb = new StringBuffer();
        while (bukkitHexMatcher.find()) {
            String raw = bukkitHexMatcher.group().replace("§x", "").replace("§", "");
            bukkitHexMatcher.appendReplacement(bukkitSb, "<#" + raw + ">");
        }
        bukkitHexMatcher.appendTail(bukkitSb);
        result = bukkitSb.toString();

        // 2. Convert &#RRGGBB format to <#RRGGBB>
        Matcher hexMatcher = HEX_AMPERSAND_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        result = sb.toString();

        // 3. Convert standalone #RRGGBB format to <#RRGGBB>
        Matcher hashMatcher = HEX_HASH_PATTERN.matcher(result);
        StringBuffer hashSb = new StringBuffer();
        while (hashMatcher.find()) {
            hashMatcher.appendReplacement(hashSb, "<#" + hashMatcher.group(1) + ">");
        }
        hashMatcher.appendTail(hashSb);
        result = hashSb.toString();

        // 4. Convert standard & and § color codes (case-insensitive) to MiniMessage tags in a single O(n) pass
        StringBuilder out = new StringBuilder(result.length() + 32);
        int len = result.length();
        for (int i = 0; i < len; i++) {
            char c = result.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(result.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> null;
                };
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }

        return out.toString();
    }

    /**
     * Parses both legacy color codes and MiniMessage tags into an Adventure Component.
     */
    public static Component parseComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        try {
            String converted = convertLegacyToMiniMessage(message);
            return MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            return LEGACY_AMPERSAND.deserialize(message);
        }
    }

    /**
     * Converts a list of text strings into a list of Adventure Components.
     */
    public static List<Component> parseComponentList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> components = new ArrayList<>(list.size());
        for (String line : list) {
            components.add(parseComponent(line));
        }
        return components;
    }

    /**
     * Serializes an Adventure Component into legacy Minecraft '§' formatted String.
     */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        return LEGACY_SECTION.serialize(component);
    }

    /**
     * Parses input text and converts it to legacy '§' formatted String.
     */
    public static String toLegacyString(String message) {
        if (message == null || message.isEmpty()) return "";
        return toLegacy(parseComponent(message));
    }

    /**
     * Serializes an Adventure Component into plain unformatted String.
     */
    public static String toPlainText(Component component) {
        if (component == null) return "";
        return PLAIN_TEXT.serialize(component);
    }

    /**
     * Sends an Adventure Component message to a CommandSender or Player.
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(parseComponent(message));
    }

    /**
     * Sends an Adventure Component to a CommandSender or Player.
     */
    public static void sendMessage(CommandSender sender, Component component) {
        if (sender == null || component == null) return;
        sender.sendMessage(component);
    }

    /**
     * Sends an ActionBar message to a player.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(parseComponent(message));
    }

    /**
     * Displays a Title and Subtitle to a player with specified timing ticks.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null) return;
        Component titleComp = title != null ? parseComponent(title) : Component.empty();
        Component subComp = subtitle != null ? parseComponent(subtitle) : Component.empty();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L)
        );

        player.showTitle(Title.title(titleComp, subComp, times));
    }
}

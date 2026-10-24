package com.verax.veraxcore.utils;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdventureUtilTest {

    @Test
    @DisplayName("Convert legacy ampersand and section codes to MiniMessage tags")
    void testConvertLegacyToMiniMessage() {
        String input = "&aHello &cWorld!";
        String result = AdventureUtil.convertLegacyToMiniMessage(input);
        assertTrue(result.contains("<green>"));
        assertTrue(result.contains("<red>"));
    }

    @Test
    @DisplayName("Convert hex ampersand format &#RRGGBB to MiniMessage tags")
    void testConvertHexAmpersand() {
        String input = "&#1B4FFFVeraxCore";
        String result = AdventureUtil.convertLegacyToMiniMessage(input);
        assertTrue(result.contains("<#1B4FFF>"));
    }

    @Test
    @DisplayName("Parse text into Adventure Component correctly")
    void testParseComponent() {
        Component component = AdventureUtil.parseComponent("&eYellow Text");
        assertNotNull(component);

        String plain = AdventureUtil.toPlainText(component);
        assertEquals("Yellow Text", plain.trim());
    }

    @Test
    @DisplayName("Serialize component to plain text")
    void testToPlainText() {
        Component component = AdventureUtil.parseComponent("<red><bold>Warning!</bold></red>");
        String plain = AdventureUtil.toPlainText(component);
        assertEquals("Warning!", plain.trim());
    }

    @Test
    @DisplayName("Serialize component to legacy formatted string")
    void testToLegacyString() {
        String legacy = AdventureUtil.toLegacyString("&cRed");
        assertTrue(legacy.contains("§c") || legacy.contains("Red"));
    }

    @Test
    @DisplayName("Handle null and empty strings gracefully")
    void testNullAndEmptyHandling() {
        assertEquals("", AdventureUtil.convertLegacyToMiniMessage(null));
        assertEquals("", AdventureUtil.convertLegacyToMiniMessage(""));

        assertEquals(Component.empty(), AdventureUtil.parseComponent(null));
        assertEquals(Component.empty(), AdventureUtil.parseComponent(""));

        assertEquals("", AdventureUtil.toPlainText(null));
        assertEquals("", AdventureUtil.toLegacy(null));

        List<Component> emptyList = AdventureUtil.parseComponentList(null);
        assertTrue(emptyList.isEmpty());
    }
}

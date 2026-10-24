package com.verax.veraxcore.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeFormatTest {

    @Test
    @DisplayName("Format 0 seconds should return fallback zero")
    void testZeroSeconds() {
        String formatted = StaffPlaytimeCommand.formatPlaytime(0, null);
        assertEquals("0 min", formatted);
    }

    @Test
    @DisplayName("Format under 60 seconds")
    void testSecondsOnly() {
        String formatted = StaffPlaytimeCommand.formatPlaytime(45, null);
        assertEquals("45 sec", formatted);
    }

    @Test
    @DisplayName("Format minutes and seconds")
    void testMinutes() {
        String formatted = StaffPlaytimeCommand.formatPlaytime(125, null);
        assertEquals("2 min", formatted);
    }

    @Test
    @DisplayName("Format hours and minutes")
    void testHoursAndMinutes() {
        // 1 hour 30 minutes = 5400 seconds
        String formatted = StaffPlaytimeCommand.formatPlaytime(5400, null);
        assertEquals("1 hour 30 min", formatted);
    }

    @Test
    @DisplayName("Format days, hours, and minutes")
    void testDaysHoursMinutes() {
        // 1 day (86400) + 2 hours (7200) + 15 minutes (900) = 94500 seconds
        String formatted = StaffPlaytimeCommand.formatPlaytime(94500, null);
        assertEquals("1 day 2 hour 15 min", formatted);
    }
}

package com.verax.veraxcore.systems;

import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.commands.StaffPlaytimeCommand;
import com.verax.veraxcore.listeners.ActivityListener;
import com.verax.veraxcore.managers.DatabaseManager;
import com.verax.veraxcore.utils.DiscordWebhookSender;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class DiscordLogSender {

    private static int getEmbedColor(FileConfiguration config) {
        try {
            String hexColor = config.getString("embed.color", "#FF5500");
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }
            return Integer.parseInt(hexColor, 16);
        } catch (Exception e) {
            return 16733440;
        }
    }

    /**
     * Formats period type with capitalized first letter and localized name from the active language.
     */
    private static String formatPeriodType(VeraxCore plugin, String rawType) {
        if (rawType == null || rawType.isEmpty()) return "";
        String typeLower = rawType.toLowerCase();

        String typeKey = "daily";
        if (typeLower.contains("hafta") || typeLower.contains("weekly")) {
            typeKey = "weekly";
        } else if (typeLower.contains("aylik") || typeLower.contains("aylık") || typeLower.contains("monthly")) {
            typeKey = "monthly";
        } else if (typeLower.contains("toplam") || typeLower.contains("total")) {
            typeKey = "total";
        }

        String localized = (plugin != null && plugin.getLanguageManager() != null)
                ? plugin.getLanguageManager().getString("period-types." + typeKey, typeKey)
                : rawType;

        if (localized == null || localized.isEmpty()) {
            localized = rawType;
        }

        return localized.substring(0, 1).toUpperCase() + localized.substring(1);
    }

    public static boolean sendStaffReport(VeraxCore plugin, String reportType) {
        FileConfiguration config = plugin.getConfig();
        String webhookUrl = config.getString("webhook-url");

        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            plugin.getLogger().warning("Could not send activity report because Discord Webhook URL is not configured!");
            return false;
        }

        String tzStr = config.getString("timezone", "Europe/Istanbul");
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tzStr);
        } catch (Exception e) {
            plugin.getLogger().warning("Timezone in config '" + tzStr + "' not found! Using default Europe/Istanbul.");
            zoneId = ZoneId.of("Europe/Istanbul");
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm");
        String dateTimeStr = now.format(formatter);

        String formattedType = formatPeriodType(plugin, reportType);

        ZonedDateTime periodStart;
        ZonedDateTime periodEnd = now;
        String reportTypeLower = reportType.toLowerCase();
        boolean isDaily = reportTypeLower.contains("gunluk") || reportTypeLower.contains("günlük") || reportTypeLower.contains("daily");

        if (reportTypeLower.contains("hafta") || reportTypeLower.contains("weekly")) {
            periodStart = now.minusDays(6);
        } else if (reportTypeLower.contains("aylik") || reportTypeLower.contains("aylık") || reportTypeLower.contains("monthly")) {
            periodStart = now.minusDays(30);
        } else if (reportTypeLower.contains("toplam") || reportTypeLower.contains("total")) {
            periodStart = now.minusDays(30);
        } else {
            periodStart = now.minusDays(1);
        }

        String periodStr;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (isDaily) {
            periodStr = now.format(dateFormatter);
        } else {
            periodStr = periodStart.format(dateFormatter) + " - " + periodEnd.format(dateFormatter);
        }

        String configTitle = config.getString("embed.title", "📅 {type} Staff Activity Report")
                .replace("{tip}", formattedType)
                .replace("{type}", formattedType);

        String configFooter = config.getString("embed.footer", "VeraxCore • {type} Report • {time}")
                .replace("{tip}", formattedType)
                .replace("{type}", formattedType)
                .replace("{saat}", dateTimeStr)
                .replace("{time}", dateTimeStr)
                .replace("{tarih_saat}", dateTimeStr);

        String format = config.getString("embed.format", "{rank}. `{staff}`: {time} {status}");
        String passedIcon = config.getString("embed.status.passed", "✅");
        String failedIcon = config.getString("embed.status.failed", "❌");
        int embedColor = getEmbedColor(config);

        boolean applyQuota = true;
        String dbType = "daily";

        if (reportType.toLowerCase().contains("hafta") || reportType.toLowerCase().contains("weekly")) {
            dbType = "weekly";
            applyQuota = false;
        } else if (reportType.toLowerCase().contains("aylik") || reportType.toLowerCase().contains("aylık") || reportType.toLowerCase().contains("monthly")) {
            dbType = "monthly";
            applyQuota = false;
        } else if (reportType.toLowerCase().contains("toplam") || reportType.toLowerCase().contains("total")) {
            dbType = "total";
            applyQuota = false;
        }

        ActivityListener.syncAllOnlinePlayers(plugin);
        Map<UUID, DatabaseManager.StaffRecord> allRecords = plugin.getDatabaseManager().getAllStaffRecords(dbType);
        for (UUID onlineUuid : ActivityListener.onlineStaffCache) {
            allRecords.putIfAbsent(onlineUuid, new DatabaseManager.StaffRecord(onlineUuid, null, 0));
        }
        StringBuilder descriptionBuilder = new StringBuilder();

        String dateRangeLabel = config.getString("embed.date-range-label", config.getString("embed.tarih-araligi-label", "📅 **Date Range:** %time%"));
        if (dateRangeLabel != null && !dateRangeLabel.isEmpty()) {
            String line;
            if (dateRangeLabel.contains("%time%") || dateRangeLabel.contains("{time}") || dateRangeLabel.contains("{aralik}")) {
                line = dateRangeLabel
                        .replace("%time%", periodStr)
                        .replace("{time}", periodStr)
                        .replace("{aralik}", periodStr);
            } else {
                line = dateRangeLabel + " " + periodStr;
            }
            descriptionBuilder.append(line).append("\n");
        }

        String reportTimeLabel = config.getString("embed.report-time-label", config.getString("embed.rapor-saati-label", ""));
        if (reportTimeLabel != null && !reportTimeLabel.isEmpty()) {
            descriptionBuilder.append(reportTimeLabel).append(" ").append(dateTimeStr).append("\n");
        }

        descriptionBuilder.append("\n");

        int targetSeconds = config.getInt("target-seconds", config.getInt("hedef-saniye", 18000));

        if (allRecords == null || allRecords.isEmpty()) {
            descriptionBuilder.append("❌ No recorded staff activity time found.");
        } else {
            java.util.Map<String, StaffReportEntry> mergedReportEntries = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, DatabaseManager.StaffRecord> entry : allRecords.entrySet()) {
                UUID uuid = entry.getKey();
                DatabaseManager.StaffRecord rec = entry.getValue();
                int accumulatedSeconds = rec != null ? rec.getSeconds() : 0;
                int sessionSeconds = 0;

                String playerName = rec != null ? rec.getUsername() : null;
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    playerName = onlinePlayer.getName();
                    if (ActivityListener.loginTimes.containsKey(uuid)) {
                        long loginTime = ActivityListener.loginTimes.get(uuid);
                        sessionSeconds = (int) ((System.currentTimeMillis() - loginTime) / 1000);
                    }
                }

                int totalSeconds = accumulatedSeconds + sessionSeconds;

                if (totalSeconds <= 0 && (onlinePlayer == null || !onlinePlayer.isOnline())) {
                    continue;
                }

                String cleanName = (playerName != null && !playerName.trim().isEmpty() && !playerName.equalsIgnoreCase("Unknown")) ? playerName : "Unknown Staff";
                String nameKey = cleanName.toLowerCase();

                if (mergedReportEntries.containsKey(nameKey)) {
                    StaffReportEntry existing = mergedReportEntries.get(nameKey);
                    int newTotal = existing.totalSeconds + totalSeconds;
                    String chosenName = existing.name;
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        chosenName = onlinePlayer.getName();
                    }
                    mergedReportEntries.put(nameKey, new StaffReportEntry(chosenName, newTotal));
                } else {
                    mergedReportEntries.put(nameKey, new StaffReportEntry(cleanName, totalSeconds));
                }
            }

            java.util.List<StaffReportEntry> entries = new java.util.ArrayList<>(mergedReportEntries.values());

            if (entries.isEmpty()) {
                descriptionBuilder.append("❌ No active staff found.");
            } else {
                entries.sort((a, b) -> Integer.compare(b.totalSeconds, a.totalSeconds));

                int rank = 1;
                for (StaffReportEntry entry : entries) {
                    String statusIcon = "";
                    if (applyQuota) {
                        statusIcon = (entry.totalSeconds >= targetSeconds) ? passedIcon : failedIcon;
                    }

                    String formattedPlaytime = StaffPlaytimeCommand.formatPlaytime(entry.totalSeconds, plugin);

                    String line = format
                            .replace("{rank}", String.valueOf(rank))
                            .replace("{sıralama}", String.valueOf(rank))
                            .replace("{staff}", entry.name)
                            .replace("{yetkili}", entry.name)
                            .replace("{time}", formattedPlaytime)
                            .replace("{sure}", formattedPlaytime)
                            .replace("{status}", statusIcon)
                            .replace("{durum}", statusIcon)
                            .trim();

                    if (!applyQuota) {
                        if (line.endsWith("-")) {
                            line = line.substring(0, line.length() - 1).trim();
                        }
                    }

                    descriptionBuilder.append(line).append("\n");
                    rank++;
                }
            }
        }

        String jsonPayload = "{"
                + "\"embeds\": [{"
                + "  \"title\": \"" + DiscordWebhookSender.escapeJson(configTitle) + "\","
                + "  \"color\": " + embedColor + ","
                + "  \"description\": \"" + DiscordWebhookSender.escapeJson(descriptionBuilder.toString()) + "\","
                + "  \"footer\": {"
                + "    \"text\": \"" + DiscordWebhookSender.escapeJson(configFooter) + "\""
                + "  }"
                + "}]"
                + "}";

        return DiscordWebhookSender.sendPayload(webhookUrl, jsonPayload, plugin.getLogger());
    }

    private static class StaffReportEntry {
        final String name;
        final int totalSeconds;

        StaffReportEntry(String name, int totalSeconds) {
            this.name = name;
            this.totalSeconds = totalSeconds;
        }
    }
}

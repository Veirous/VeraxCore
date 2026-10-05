
package com.verax.veraxcore.commands;

import com.verax.veraxcore.systems.DiscordLogSender;
import com.verax.veraxcore.VeraxCore;
import com.verax.veraxcore.listeners.ActivityListener;
import com.verax.veraxcore.managers.DatabaseManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaffPlaytimeCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public StaffPlaytimeCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public static String colorize(String message) {
        if (message == null || message.trim().isEmpty()) return "";

        VeraxCore instance = VeraxCore.getInstance();
        if (instance != null && instance.getLanguageManager() != null) {
            String prefix = instance.getLanguageManager().getPrefix();
            message = message.replace("<prefix>", prefix).replace("%prefix%", prefix);
            return instance.getLanguageManager().translateHexColorCodes(message);
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : color.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String renkledir(String mesaj) {
        return colorize(mesaj);
    }

    /** Config/Language-aware version — reads time unit labels from time-format section */
    public static String formatPlaytime(int totalSeconds, VeraxCore plugin) {
        String labelDay    = (plugin != null ? plugin.getLanguageManager().getString("time-format.day",    "day")  : "day").trim();
        String labelHour   = (plugin != null ? plugin.getLanguageManager().getString("time-format.hour",   "hour") : "hour").trim();
        String labelMinute = (plugin != null ? plugin.getLanguageManager().getString("time-format.minute", "min")  : "min").trim();
        String labelSecond = (plugin != null ? plugin.getLanguageManager().getString("time-format.second", "sec")  : "sec").trim();
        String labelZero   = (plugin != null ? plugin.getLanguageManager().getString("time-format.zero",   "0 min"): "0 min").trim();

        if (totalSeconds <= 0) return labelZero;
        if (totalSeconds < 60) return totalSeconds + " " + labelSecond;

        int days = totalSeconds / 86400;
        int remSecs = totalSeconds % 86400;
        int hours = remSecs / 3600;
        remSecs %= 3600;
        int minutes = remSecs / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)  sb.append(days).append(" ").append(labelDay).append(" ");
        if (hours > 0) sb.append(hours).append(" ").append(labelHour).append(" ");
        sb.append(minutes).append(" ").append(labelMinute);
        return sb.toString().trim();
    }

    private String t(String path, FileConfiguration config) {
        String canonicalKey = path;
        String aliasKey = path;
        switch (path) {
            case "admin-yetki-yok":
            case "admin-no-permission":
                canonicalKey = "admin-no-permission";
                aliasKey = "admin-yetki-yok";
                break;
            case "yetki-yok":
            case "no-permission":
                canonicalKey = "no-permission";
                aliasKey = "yetki-yok";
                break;
            case "header":
            case "ust-baslik":
                canonicalKey = "header";
                aliasKey = "ust-baslik";
                break;
            case "passed-icon":
            case "baraj-gecen-icon":
                canonicalKey = "passed-icon";
                aliasKey = "baraj-gecen-icon";
                break;
            case "failed-icon":
            case "baraj-kalan-icon":
                canonicalKey = "failed-icon";
                aliasKey = "baraj-kalan-icon";
                break;
            case "log-preparing":
            case "log-hazirlaniyor":
                canonicalKey = "log-preparing";
                aliasKey = "log-hazirlaniyor";
                break;
            case "log-success":
            case "log-basarili":
                canonicalKey = "log-success";
                aliasKey = "log-basarili";
                break;
            case "list-format":
            case "liste-formati":
            case "liste-formati-genel":
                canonicalKey = "list-format";
                aliasKey = "liste-formati-genel";
                break;
        }

        String msg = plugin.getLanguageManager().getString("staff-messages." + canonicalKey, "");
        if (msg == null || msg.isEmpty()) {
            msg = plugin.getLanguageManager().getString("staff-messages." + aliasKey, "");
        }
        if (msg == null || msg.isEmpty()) {
            msg = plugin.getLanguageManager().getString("mesajlar." + canonicalKey, "");
        }
        if (msg == null || msg.isEmpty()) {
            msg = plugin.getLanguageManager().getString("mesajlar." + aliasKey, "");
        }
        if (msg == null || msg.isEmpty()) {
            switch (canonicalKey) {
                case "admin-no-permission": return colorize("&cYou do not have permission to use this admin command!");
                case "no-permission": return colorize("&cYou cannot do this");
                case "header": return colorize("&#1B4FFF--- &#1BA1FF{status} Status &#1B4FFF---");
                case "passed-icon": return "&#00FF00✔";
                case "failed-icon": return "&#ff0000✘";
                case "log-preparing": return colorize("&#1BA1FFDiscord report is being prepared and sent...");
                case "log-success": return colorize("&#00FF00Discord report sent successfully!");
                default: return "";
            }
        }
        return colorize(msg);
    }

    private boolean hasStaffRank(Player player, FileConfiguration config) {
        return ActivityListener.hasStaffRankStatic(plugin, player);
    }

    private boolean hasOfflineStaffRank(UUID uuid, FileConfiguration config) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return hasStaffRank(onlinePlayer, config);
        }

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user != null) {
                List<String> rawRanks = config.getStringList("allowed-ranks");
                if (rawRanks.isEmpty()) {
                    rawRanks = config.getStringList("izin-verilen-ranklar");
                }
                final List<String> allowedRanks = rawRanks;
                String primaryGroup = user.getPrimaryGroup().toLowerCase();
                
                boolean primaryMatch = allowedRanks.stream()
                        .map(String::toLowerCase)
                        .anyMatch(primaryGroup::equals);
                if (primaryMatch) return true;

                boolean nodeMatch = user.getNodes().stream().anyMatch(node -> {
                    String key = node.getKey().toLowerCase();
                    if (key.equals("veraxcore.stafftime.view") || key.equals("veraxcore.yetkilisure.gor")) return true;
                    if (key.startsWith("group.")) {
                        String groupName = key.substring(6);
                        return allowedRanks.stream().anyMatch(r -> r.equalsIgnoreCase(groupName));
                    }
                    return false;
                });
                if (nodeMatch) return true;
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    private String getPeriodKeyword(String type) {
        return plugin.getLanguageManager().getString("period-types." + type, type).toLowerCase(Locale.ROOT);
    }

    private String getStatusDisplayName(String type) {
        String lang = plugin != null ? plugin.getLanguageManager().getCurrentLanguage() : "tr";
        if ("tr".equalsIgnoreCase(lang)) {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "daily": return "Günlük";
                case "weekly": return "Haftalık";
                case "monthly": return "Aylık";
                case "total": return "Toplam";
            }
        }
        String kw = getPeriodKeyword(type);
        if (kw != null && !kw.isEmpty()) {
            return kw.substring(0, 1).toUpperCase(Locale.ROOT) + kw.substring(1);
        }
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
    }

    private String resolvePeriodType(String arg) {
        if (arg == null) return null;
        String aRoot = arg.toLowerCase(Locale.ROOT);
        String aTr = arg.toLowerCase(new Locale("tr", "TR"));
        String dailyKw = getPeriodKeyword("daily");
        String weeklyKw = getPeriodKeyword("weekly");
        String monthlyKw = getPeriodKeyword("monthly");
        String totalKw = getPeriodKeyword("total");

        if (aRoot.equals(dailyKw) || aTr.equals(dailyKw) || aRoot.equals("daily") || aTr.equals("daily") || aRoot.equals("gunluk") || aTr.equals("günlük")) return "daily";
        if (aRoot.equals(weeklyKw) || aTr.equals(weeklyKw) || aRoot.equals("weekly") || aTr.equals("weekly") || aRoot.equals("haftalik") || aTr.equals("haftalık")) return "weekly";
        if (aRoot.equals(monthlyKw) || aTr.equals(monthlyKw) || aRoot.equals("monthly") || aTr.equals("monthly") || aRoot.equals("aylik") || aTr.equals("aylık")) return "monthly";
        if (aRoot.equals(totalKw) || aTr.equals(totalKw) || aRoot.equals("total") || aTr.equals("total") || aRoot.equals("toplam") || aTr.equals("toplam")) return "total";
        return null;
    }

    private List<String> getPeriodTypes() {
        return Arrays.asList(
            getPeriodKeyword("daily"),
            getPeriodKeyword("weekly"),
            getPeriodKeyword("monthly"),
            getPeriodKeyword("total")
        );
    }

    private void ensureOnlineStaffLoaded() {
        ActivityListener.syncAllOnlinePlayers(plugin);
        FileConfiguration config = plugin.getConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hasStaffRank(p, config)) {
                plugin.getDatabaseManager().updateUsername(p.getUniqueId(), p.getName());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();

        // Module check
        if (!config.getBoolean("modules.staff-time", config.getBoolean("modules.yetkili-sure", true))) {
            String disabledMsg = plugin.getMessage("messages.module-disabled");
            if (disabledMsg == null || disabledMsg.isEmpty()) {
                String prefix = config.getString("prefix", "");
                disabledMsg = colorize(prefix + "&cThis feature is currently disabled!");
            }
            sender.sendMessage(disabledMsg);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("sıfırla") || args[0].equalsIgnoreCase("reset"))) {
            if (!sender.hasPermission("veraxcore.stafftime.reset") && !sender.hasPermission("veraxcore.yetkilisure.sifirla") && !sender.isOp()) {
                String mesaj = t("admin-yetki-yok", config);
                if (!mesaj.isEmpty()) sender.sendMessage(mesaj);
                return true;
            }

            if (args.length < 2) {
                String kw = getPeriodKeyword("daily") + "/" + getPeriodKeyword("weekly") + "/" + getPeriodKeyword("monthly") + "/" + getPeriodKeyword("total");
                sender.sendMessage(colorize("&cUsage: /" + label.toLowerCase() + " reset <" + kw + ">"));
                return true;
            }

            String dbType = resolvePeriodType(args[1]);
            if (dbType == null) {
                String kw = getPeriodKeyword("daily") + "/" + getPeriodKeyword("weekly") + "/" + getPeriodKeyword("monthly") + "/" + getPeriodKeyword("total");
                sender.sendMessage(colorize("&cInvalid category! Usage: /" + label.toLowerCase() + " reset <" + kw + ">"));
                return true;
            }
            String typeName = dbType.substring(0, 1).toUpperCase() + dbType.substring(1);

            plugin.getDatabaseManager().clearSecondsByType(dbType);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (hasStaffRank(p, config)) {
                    if (ActivityListener.loginTimes.containsKey(p.getUniqueId())) {
                        ActivityListener.loginTimes.put(p.getUniqueId(), System.currentTimeMillis());
                    }
                }
            }

            sender.sendMessage(colorize("&#FF3333✔ " + typeName + " activity times for all staff have been successfully reset!"));
            plugin.getLogger().info("[System] " + typeName + " activity times reset by " + sender.getName() + ".");
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("loggönder") || args[0].equalsIgnoreCase("sendlog"))) {
            if (!sender.hasPermission("veraxcore.stafftime.sendlog") && !sender.hasPermission("veraxcore.yetkilisure.loggonder") && !sender.isOp()) {
                String mesaj = t("admin-yetki-yok", config);
                if (!mesaj.isEmpty()) sender.sendMessage(mesaj);
                return true;
            }
            
            String logTypeArg = (args.length > 1) ? args[1].toLowerCase() : getPeriodKeyword("daily");
            String resolvedLogType = resolvePeriodType(logTypeArg);
            if (resolvedLogType == null) {
                String kw = getPeriodKeyword("daily") + "/" + getPeriodKeyword("weekly") + "/" + getPeriodKeyword("monthly") + "/" + getPeriodKeyword("total");
                sender.sendMessage(colorize("&cUsage: /" + label.toLowerCase() + " sendlog <" + kw + ">"));
                return true;
            }

            String preparingMsg = t("log-hazirlaniyor", config);
            if (!preparingMsg.isEmpty()) sender.sendMessage(preparingMsg);
            
            final String periodToSend = resolvedLogType;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = DiscordLogSender.sendStaffReport(plugin, periodToSend);
                if (success) {
                    String successMsg = t("log-basarili", config);
                    if (!successMsg.isEmpty()) sender.sendMessage(successMsg);
                } else {
                    sender.sendMessage(colorize("&c✘ Discord Webhook could not be sent! Please check the Webhook URL in config.yml and the console."));
                }
            });
            return true;
        }

        ensureOnlineStaffLoaded();

        String headerSuffix = getStatusDisplayName("daily");
        String selectedType = "daily";
        boolean applyThreshold = true;

        if (args.length > 0) {
            String resolved = resolvePeriodType(args[0]);
            if ("weekly".equals(resolved)) {
                selectedType = "weekly";
                headerSuffix = getStatusDisplayName("weekly");
                applyThreshold = false;
            } else if ("monthly".equals(resolved)) {
                selectedType = "monthly";
                headerSuffix = getStatusDisplayName("monthly");
                applyThreshold = false;
            } else if ("total".equals(resolved)) {
                selectedType = "total";
                headerSuffix = getStatusDisplayName("total");
                applyThreshold = false;
            }
        }

        if (sender instanceof Player && !hasStaffRank((Player) sender, config)) {
            String noPermMsg = t("yetki-yok", config);
            if (!noPermMsg.isEmpty()) sender.sendMessage(noPermMsg);
            return true;
        }

        final String finalSelectedType = selectedType;
        final String finalHeaderSuffix = headerSuffix;
        final boolean finalApplyThreshold = applyThreshold;
        final String finalPassedIcon = t("passed-icon", config);
        final String finalFailedIcon = t("failed-icon", config);
        final int finalTarget = config.getInt("target-seconds", config.getInt("hedef-saniye", 7200));
        final String finalFormat = plugin.getLanguageManager().getString("staff-messages.list-format", plugin.getLanguageManager().getString("mesajlar.liste-formati-genel", "&#1BA1FF{rank}. &#1ba1ff{staff}: &F{time} {status}"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, DatabaseManager.StaffRecord> allRecords = plugin.getDatabaseManager().getAllStaffRecords(finalSelectedType);
            for (UUID onlineUuid : ActivityListener.onlineStaffCache) {
                allRecords.putIfAbsent(onlineUuid, new DatabaseManager.StaffRecord(onlineUuid, null, 0));
            }

            Map<String, StaffEntry> mergedEntries = new java.util.LinkedHashMap<>();

            for (Map.Entry<UUID, DatabaseManager.StaffRecord> entry : allRecords.entrySet()) {
                UUID uuid = entry.getKey();
                DatabaseManager.StaffRecord rec = entry.getValue();

                if (!hasOfflineStaffRank(uuid, config)) {
                    continue;
                }

                int accumulatedSeconds = rec != null ? rec.getSeconds() : 0;
                int sessionSeconds = 0;

                String playerName = rec != null ? rec.getUsername() : null;
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    playerName = onlinePlayer.getName();

                    if (ActivityListener.loginTimes.containsKey(uuid)) {
                        long joinTime = ActivityListener.loginTimes.get(uuid);
                        sessionSeconds = (int) ((System.currentTimeMillis() - joinTime) / 1000);
                    }
                }

                int totalSecs = accumulatedSeconds + sessionSeconds;

                // Do not display offline players with 0 seconds
                if (totalSecs <= 0 && (onlinePlayer == null || !onlinePlayer.isOnline())) {
                    continue;
                }

                String cleanName = (playerName != null && !playerName.trim().isEmpty() && !playerName.equalsIgnoreCase("Unknown")) ? playerName : "Unknown Staff";
                String nameKey = cleanName.toLowerCase();

                if (mergedEntries.containsKey(nameKey)) {
                    StaffEntry existing = mergedEntries.get(nameKey);
                    int combinedTime = existing.totalSeconds + totalSecs;
                    String chosenName = existing.name;
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        chosenName = onlinePlayer.getName();
                    }
                    mergedEntries.put(nameKey, new StaffEntry(existing.uuid, chosenName, combinedTime));
                } else {
                    mergedEntries.put(nameKey, new StaffEntry(uuid, cleanName, totalSecs));
                }
            }

            List<StaffEntry> staffEntries = new ArrayList<>(mergedEntries.values());

            if (staffEntries.isEmpty()) {
                sender.sendMessage(colorize("&cNo active staff found."));
                return;
            }

            // Sort by total seconds descending
            staffEntries.sort((a, b) -> Integer.compare(b.totalSeconds, a.totalSeconds));

            List<String> outputLines = new ArrayList<>();
            String rawHeader = plugin.getLanguageManager().getString("staff-messages.header", plugin.getLanguageManager().getString("mesajlar.ust-baslik", "&#1B4FFF--- &#1BA1FF{status} Durumu &#1B4FFF---"));
            if (rawHeader != null && !rawHeader.isEmpty()) {
                rawHeader = rawHeader.replace("{status}", finalHeaderSuffix).replace("{durum}", finalHeaderSuffix);
                outputLines.add(colorize(rawHeader));
            }

            int rankIndex = 1;
            for (StaffEntry entry : staffEntries) {
                String status = "";
                if (finalApplyThreshold) {
                    status = (entry.totalSeconds >= finalTarget) ? finalPassedIcon : finalFailedIcon;
                }

                String dynamicTimeStr = formatPlaytime(entry.totalSeconds, plugin);

                String line = finalFormat
                        .replace("{rank}", String.valueOf(rankIndex))
                        .replace("{sıralama}", String.valueOf(rankIndex))
                        .replace("{status}", status)
                        .replace("{durum}", status)
                        .replace("{staff}", entry.name)
                        .replace("{yetkili}", entry.name)
                        .replace("{time}", dynamicTimeStr)
                        .replace("{sure}", dynamicTimeStr);

                line = line.replace("  ", " ");

                if (!finalApplyThreshold && line.endsWith("-")) {
                    line = line.substring(0, line.length() - 1).trim();
                }

                if (!line.trim().isEmpty()) {
                    outputLines.add(colorize(line));
                }
                rankIndex++;
            }

            for (String outLine : outputLines) {
                sender.sendMessage(outLine);
            }
        });

        return true;
    }

    private static class StaffEntry {
        final UUID uuid;
        final String name;
        final int totalSeconds;

        StaffEntry(UUID uuid, String name, int totalSeconds) {
            this.uuid = uuid;
            this.name = name;
            this.totalSeconds = totalSeconds;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("modules.staff-time", config.getBoolean("modules.yetkili-sure", true))) {
            return completions;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!hasStaffRank(player, config)) {
                return completions;
            }
        }

        if (args.length == 1) {
            List<String> firstArgs = new ArrayList<>(getPeriodTypes());
            firstArgs.remove(getPeriodKeyword("daily"));

            if (sender.hasPermission("veraxcore.stafftime.reset") || sender.hasPermission("veraxcore.yetkilisure.sifirla") || sender.isOp()) {
                firstArgs.add("sendlog");
                firstArgs.add("reset");
            }

            for (String s : firstArgs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("loggönder") || args[0].equalsIgnoreCase("sendlog")
                    || args[0].equalsIgnoreCase("sıfırla") || args[0].equalsIgnoreCase("reset")) {
                if (sender.hasPermission("veraxcore.stafftime.sendlog") || sender.hasPermission("veraxcore.yetkilisure.loggonder") || sender.isOp()) {
                    for (String t : getPeriodTypes()) {
                        if (t.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(t);
                        }
                    }
                }
            }
        }
        return completions;
    }
}

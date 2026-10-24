package com.verax.veraxcore.managers;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");
    private final VeraxCore plugin;
    private FileConfiguration langConfig;
    private String currentLanguage = "tr";

    public LanguageManager(VeraxCore plugin) {
        this.plugin = plugin;
        setupLanguages();
    }

    /**
     * Initializes language files, fills in missing keys, and loads the active language.
     */
    public void setupLanguages() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Extract and update supported built-in languages
        saveDefaultLanguageFile("tr.yml");
        saveDefaultLanguageFile("en.yml");
        saveDefaultLanguageFile("de.yml");

        // Determine and load the active language
        loadActiveLanguage();
    }

    /**
     * Saves built-in language files from the jar to disk and safely merges new keys.
     */
    private void saveDefaultLanguageFile(String fileName) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!langFile.exists()) {
            try {
                plugin.saveResource("lang/" + fileName, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save default lang file: " + fileName);
            }
        } else {
            com.verax.veraxcore.utils.ConfigUpdater.update(plugin, "lang/" + fileName, langFile);
        }
    }

    /**
     * Loads the language specified by the 'lang' setting in config.yml.
     */
    public void loadActiveLanguage() {
        String langSetting = plugin.getConfig().getString("lang", "tr").toLowerCase(java.util.Locale.ROOT).trim();

        // Language normalization
        if (langSetting.equals("tr") || langSetting.equals("tur") || langSetting.equals("turkish") || langSetting.equals("turkce")) {
            currentLanguage = "tr";
        } else if (langSetting.equals("en") || langSetting.equals("eng") || langSetting.equals("english") || langSetting.equals("ingilizce")) {
            currentLanguage = "en";
        } else if (langSetting.equals("de") || langSetting.equals("ger") || langSetting.equals("german") || langSetting.equals("almanca") || langSetting.equals("deutsch")) {
            currentLanguage = "de";
        } else {
            // Custom or specified file name
            currentLanguage = langSetting;
        }

        File langFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage + ".yml");
        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage);
        }

        if (!langFile.exists()) {
            plugin.getLogger().warning("[Language] Language file '" + currentLanguage + ".yml' not found! Falling back to 'tr.yml'.");
            currentLanguage = "tr";
            langFile = new File(plugin.getDataFolder(), "lang/tr.yml");
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
            langConfig = YamlConfiguration.loadConfiguration(reader);
            plugin.getLogger().info("[Language] Active language loaded: " + currentLanguage.toUpperCase(java.util.Locale.ROOT) + " (" + langFile.getName() + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("[Language] Failed to load language file (" + langFile.getName() + "): " + e.getMessage());
            langConfig = new YamlConfiguration();
        }
    }

    public void reload() {
        setupLanguages();
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public FileConfiguration getLangConfig() {
        if (langConfig == null) {
            loadActiveLanguage();
        }
        return langConfig;
    }

    public String getPrefix() {
        return plugin.getConfig().getString("prefix", "&#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8»");
    }

    public boolean contains(String path) {
        if (langConfig != null && langConfig.contains(path)) return true;
        return plugin.getConfig().contains(path);
    }

    public String getRawMessage(String path) {
        if (langConfig != null && langConfig.contains(path)) {
            return langConfig.getString(path, "");
        }
        return plugin.getConfig().getString(path, "");
    }

    public String getString(String path, String def) {
        if (langConfig != null && langConfig.contains(path)) {
            return langConfig.getString(path, def);
        }
        return plugin.getConfig().getString(path, def);
    }

    public String getString(String path) {
        return getString(path, "");
    }

    public List<String> getStringList(String path) {
        if (langConfig != null && langConfig.contains(path)) {
            return langConfig.getStringList(path);
        }
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getStringList(path);
        }
        return Collections.emptyList();
    }

    public ConfigurationSection getConfigurationSection(String path) {
        if (langConfig != null && langConfig.contains(path)) {
            return langConfig.getConfigurationSection(path);
        }
        return plugin.getConfig().getConfigurationSection(path);
    }

    public String getMessage(String path) {
        return getMessage(path, null);
    }

    public String getMessage(String path, String playerName) {
        String msg = "";
        if (langConfig != null && langConfig.contains(path)) {
            msg = langConfig.getString(path, "");
        } else if (plugin.getConfig().contains(path)) {
            msg = plugin.getConfig().getString(path, "");
        }

        if (msg == null || msg.isEmpty()) return "";

        String prefix = getPrefix();
        msg = msg.replace("<prefix>", prefix).replace("%prefix%", prefix);

        if (playerName != null) {
            msg = msg.replace("{player}", playerName).replace("%player%", playerName);
            Player onlineP = Bukkit.getPlayerExact(playerName);
            if (onlineP != null) {
                msg = com.verax.veraxcore.hooks.PlaceholderAPIHook.formatWithPAPI(onlineP, msg);
            }
        }

        return translateHexColorCodes(msg);
    }

    /**
     * Returns the message as a Paper Adventure Component (supports MiniMessage, Gradient, Hover, Click).
     */
    public net.kyori.adventure.text.Component getMessageComponent(String path) {
        return getMessageComponent(path, null);
    }

    /**
     * Returns the message as a Paper Adventure Component with player name placeholder formatted.
     */
    public net.kyori.adventure.text.Component getMessageComponent(String path, String playerName) {
        String msg = "";
        if (langConfig != null && langConfig.contains(path)) {
            msg = langConfig.getString(path, "");
        } else if (plugin.getConfig().contains(path)) {
            msg = plugin.getConfig().getString(path, "");
        }

        if (msg == null || msg.isEmpty()) return net.kyori.adventure.text.Component.empty();

        String prefix = getPrefix();
        msg = msg.replace("<prefix>", prefix).replace("%prefix%", prefix);

        if (playerName != null) {
            msg = msg.replace("{player}", playerName).replace("%player%", playerName);
        }

        return com.verax.veraxcore.utils.AdventureUtil.parseComponent(msg);
    }

    /**
     * Sends an Adventure Component message directly to a CommandSender / Player target.
     */
    public void sendMessage(org.bukkit.command.CommandSender sender, String path) {
        sendMessage(sender, path, null);
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path, String playerName) {
        if (sender == null) return;
        net.kyori.adventure.text.Component comp = getMessageComponent(path, playerName);
        com.verax.veraxcore.utils.AdventureUtil.sendMessage(sender, comp);
    }

    public String formatTime(long totalSecs) {
        String dayLabel = getString("time-format.day", "d");
        String hourLabel = getString("time-format.hour", "h");
        String minLabel = getString("time-format.minute", "m");
        String secLabel = getString("time-format.second", "s");
        String zeroLabel = getString("time-format.zero", "0 " + minLabel);

        if (totalSecs <= 0) return zeroLabel;

        long days = totalSecs / 86400;
        long hours = (totalSecs % 86400) / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(dayLabel).append(" ");
        }
        if (hours > 0) {
            sb.append(hours).append(hourLabel).append(" ");
        }
        if (minutes > 0 || (days == 0 && hours > 0 && seconds > 0)) {
            sb.append(minutes).append(minLabel).append(" ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append(secLabel);
        }
        return sb.toString().trim();
    }

    public String translateHexColorCodes(String message) {
        if (message == null || message.isEmpty()) return "";
        // If message contains MiniMessage tags (<gradient, <rainbow, <#..., etc.)
        // AdventureUtil.toLegacyString resolves both MiniMessage gradients and legacy & / &#hex codes.
        if (message.contains("<") && message.contains(">")) {
            return com.verax.veraxcore.utils.AdventureUtil.toLegacyString(message);
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + group).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}

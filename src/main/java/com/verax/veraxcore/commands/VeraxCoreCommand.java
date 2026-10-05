package com.verax.veraxcore.commands;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VeraxCoreCommand implements CommandExecutor, TabCompleter {

    private final VeraxCore plugin;

    public VeraxCoreCommand(VeraxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veraxcore.admin") && !sender.isOp()) {
            sender.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                handleReload(sender);
                break;

            case "info":
            case "version":
                handleInfo(sender);
                break;

            case "modules":
                handleModules(sender);
                break;

            case "help":
            default:
                sendHelp(sender, label);
                break;
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        String oldStorageType = plugin.getConfig().getString("storage-type", "SQLITE");

        plugin.setupConfig();
        plugin.reloadDatabaseIfNeeded(oldStorageType);
        if (plugin.getChatControlListener() != null) {
            plugin.getChatControlListener().reloadWordActions();
        }
        if (plugin.getAutoAnnouncementSystem() != null) {
            plugin.getAutoAnnouncementSystem().reload();
        }
        if (plugin.getMaintenanceManager() != null && plugin.getMaintenanceManager().isMaintenanceActive()) {
            plugin.getMaintenanceManager().startActiveMaintenanceBossBar();
        }
        sender.sendMessage(plugin.getMessage("messages.reload-success"));
    }

    private void handleInfo(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long allocatedMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = allocatedMemory - freeMemory;

        String dbType = plugin.getDatabaseManager() != null
                ? plugin.getDatabaseManager().getStorageType()
                : plugin.getConfig().getString("storage-type", "SQLITE");

        String timezone = plugin.getConfig().getString("timezone", "Europe/Istanbul");
        String version = plugin.getDescription().getVersion();
        int totalModules = getModulesMap().size();
        long activeModules = getModulesMap().values().stream().filter(Boolean::booleanValue).count();

        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &fSystem Overview"));
        sender.sendMessage(plugin.translateHexColorCodes(""));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fVersion: &av" + version));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fDeveloper: &fVeraxDev"));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fPlatform: &e" + Bukkit.getName() + " " + Bukkit.getBukkitVersion()));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fDatabase: &a" + dbType));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fTimezone: &b" + timezone));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fActive Modules: &#1BA1FF" + activeModules + "&7/&f" + totalModules));
        sender.sendMessage(plugin.translateHexColorCodes("  &7• &fMemory: &e" + usedMemory + "MB &7/ &6" + maxMemory + "MB"));
        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
    }

    private void handleModules(CommandSender sender) {
        Map<String, Boolean> modules = getModulesMap();
        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &fModule Status"));
        sender.sendMessage(plugin.translateHexColorCodes(""));

        for (Map.Entry<String, Boolean> entry : modules.entrySet()) {
            String name = entry.getKey();
            boolean enabled = entry.getValue();
            String statusBadge = enabled ? "&#2ECC71[✔ Enabled]" : "&#E74C3C[✘ Disabled]";
            sender.sendMessage(plugin.translateHexColorCodes("  &7• &f" + name + ": " + statusBadge));
        }

        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1B4FFFV&#1B58FFe&#1B61FFr&#1B6AFFa&#1B73FFx&#1B7DFFC&#1B86FFo&#1B98FFr&#1BA1FFe &8» &fCommand Help"));
        sender.sendMessage(plugin.translateHexColorCodes(""));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1BA1FF/" + label.toLowerCase() + " reload &7- Reload plugin config and language files"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1BA1FF/" + label.toLowerCase() + " info &7- View plugin version and system overview"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1BA1FF/" + label.toLowerCase() + " modules &7- Check status of all feature modules"));
        sender.sendMessage(plugin.translateHexColorCodes("  &#1BA1FF/" + label.toLowerCase() + " help &7- Display this help message"));
        sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
    }

    private Map<String, Boolean> getModulesMap() {
        Map<String, Boolean> modules = new LinkedHashMap<>();
        modules.put("Trade", plugin.getConfig().getBoolean("modules.trade", plugin.getConfig().getBoolean("modules.ticaret", true)));
        modules.put("Staff Time", plugin.getConfig().getBoolean("modules.staff-time", plugin.getConfig().getBoolean("modules.yetkili-sure", true)));
        modules.put("Staff Chat", plugin.getConfig().getBoolean("modules.staff-chat", plugin.getConfig().getBoolean("modules.yetkili-sohbet", true)));
        modules.put("Report", plugin.getConfig().getBoolean("modules.report", plugin.getConfig().getBoolean("modules.rapor", true)));
        modules.put("Spawn", plugin.getConfig().getBoolean("modules.spawn", true));
        modules.put("Chat Control", plugin.getConfig().getBoolean("modules.chat", plugin.getConfig().getBoolean("modules.sohbet", true)));
        modules.put("Announcement", plugin.getConfig().getBoolean("modules.announcement", plugin.getConfig().getBoolean("modules.duyuru", true)));
        modules.put("Verification", plugin.getConfig().getBoolean("modules.verification", plugin.getConfig().getBoolean("modules.dogrulama", true)));
        modules.put("Trash Bin", plugin.getConfig().getBoolean("modules.trash", plugin.getConfig().getBoolean("modules.cop", true)));
        modules.put("Auto Announcement", plugin.getConfig().getBoolean("modules.auto-announcement", plugin.getConfig().getBoolean("modules.otoduyuru", true)));
        modules.put("Player Vault (PV)", plugin.getConfig().getBoolean("modules.pv", true));
        modules.put("Word Actions", plugin.getConfig().getBoolean("modules.word-actions", plugin.getConfig().getBoolean("modules.kelime-eylemleri", true)));
        modules.put("Maintenance", plugin.getConfig().getBoolean("modules.maintenance", plugin.getConfig().getBoolean("modules.bakim", true)));
        modules.put("Auto Restart", plugin.getConfig().getBoolean("modules.auto-restart", plugin.getConfig().getBoolean("modules.otorestart", true)));
        return modules;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && (sender.hasPermission("veraxcore.admin") || sender.isOp())) {
            List<String> options = Arrays.asList("reload", "info", "version", "modules", "help");
            String prefix = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(prefix)) {
                    completions.add(opt);
                }
            }
        }
        return completions;
    }
}

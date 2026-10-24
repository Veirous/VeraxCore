package com.verax.veraxcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class StaffPlaytimeTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("weekly");
            completions.add("monthly");
            completions.add("total");
            if (sender.hasPermission("veraxcore.stafftime.reset") || sender.hasPermission("veraxcore.yetkilisure.sifirla") 
                    || sender.hasPermission("veraxcore.stafftime.sendlog") || sender.hasPermission("veraxcore.yetkilisure.loggonder") || sender.isOp()) {
                completions.add("sendlog");
                completions.add("reset");
                completions.add("reload");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("loggönder") || args[0].equalsIgnoreCase("sendlog") || args[0].equalsIgnoreCase("sıfırla") || args[0].equalsIgnoreCase("reset"))) {
            if (sender.hasPermission("veraxcore.stafftime.sendlog") || sender.hasPermission("veraxcore.yetkilisure.loggonder") || sender.isOp()) {
                completions.add("daily");
                completions.add("weekly");
                completions.add("monthly");
                completions.add("total");
            }
        }
        return completions;
    }
}

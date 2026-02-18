package com.simpleplugins.simplertp.command;

import com.simpleplugins.simplertp.SimpleRTP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SrtpCommand implements CommandExecutor {

    private final SimpleRTP plugin;

    public SrtpCommand(SimpleRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            return false; // show usage
        }
        if (!sender.hasPermission("srtp.reload")) {
            plugin.sendConfigMessage(sender, "reload-no-permission");
            return true;
        }
        plugin.reloadConfig();
        plugin.refreshEconomy();
        plugin.sendConfigMessage(sender, "reload-success");
        return true;
    }
}


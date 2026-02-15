package com.sardidefcon.simplertp;

import com.sardidefcon.simplertp.command.RTPCommand;
import com.sardidefcon.simplertp.command.SrtpCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleRTP extends JavaPlugin {

    private Object economyProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshEconomy();
        getCommand("rtp").setExecutor(new RTPCommand(this));
        getCommand("srtp").setExecutor(new SrtpCommand(this));
    }

    /**
     * Sends a config message to the sender (prefix + message key), with & color codes translated to section sign.
     */
    public void sendConfigMessage(CommandSender sender, String messageKey) {
        String prefix = getConfig().getString("prefix", "");
        String msg = getConfig().getString("messages." + messageKey, "&7[" + messageKey + "]");
        String text = (prefix != null && !prefix.isEmpty()) ? prefix + msg : msg;
        text = text.replace('&', '\u00A7');
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);
        sender.sendMessage(component);
    }

    /**
     * Loads the Vault economy provider if available (reflection, no direct Vault dependency).
     * Call after reloadConfig() to refresh.
     */
    public void refreshEconomy() {
        economyProvider = null;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = getServer().getServicesManager().getRegistration(economyClass);
            if (rsp != null) {
                economyProvider = rsp.getProvider();
            }
        } catch (ClassNotFoundException ignored) {
            // Vault not installed
        }
    }

    /**
     * Vault economy provider (null if no Vault/economy). Use with VaultEconomyHelper.
     */
    public Object getEconomyProvider() {
        return economyProvider;
    }
}

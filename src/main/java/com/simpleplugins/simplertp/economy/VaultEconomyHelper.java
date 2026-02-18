package com.simpleplugins.simplertp.economy;

import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/**
 * Access to Vault Economy via reflection so the plugin does not depend on Vault at compile/load time.
 */
public final class VaultEconomyHelper {

    private VaultEconomyHelper() {}

    /**
     * Checks whether the player has at least the given amount.
     */
    public static boolean hasEnough(Object economyProvider, OfflinePlayer player, double amount) {
        if (economyProvider == null) return false;
        try {
            Method getBalance = economyProvider.getClass().getMethod("getBalance", OfflinePlayer.class);
            Double balance = (Double) getBalance.invoke(economyProvider, player);
            return balance != null && balance >= amount;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether the player has an economy account.
     */
    public static boolean hasAccount(Object economyProvider, OfflinePlayer player) {
        if (economyProvider == null) return false;
        try {
            Method hasAccount = economyProvider.getClass().getMethod("hasAccount", OfflinePlayer.class);
            return Boolean.TRUE.equals(hasAccount.invoke(economyProvider, player));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Withdraws the amount from the player. Returns true if the transaction succeeded.
     */
    public static boolean withdraw(Object economyProvider, OfflinePlayer player, double amount) {
        if (economyProvider == null) return false;
        try {
            Method withdraw = economyProvider.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Object response = withdraw.invoke(economyProvider, player, amount);
            if (response == null) return false;
            Method transactionSuccess = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(transactionSuccess.invoke(response));
        } catch (Exception e) {
            return false;
        }
    }
}


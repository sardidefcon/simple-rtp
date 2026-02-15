package com.sardidefcon.simplertp.command;

import com.sardidefcon.simplertp.SimpleRTP;
import com.sardidefcon.simplertp.economy.VaultEconomyHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RTPCommand implements CommandExecutor {

    private static final int MAX_ATTEMPTS = 20;

    private final SimpleRTP plugin;
    private final Random random = ThreadLocalRandom.current();
    private final Map<java.util.UUID, Long> cooldownEndByUuid = new HashMap<>();

    private NamespacedKey getUsedRTPOnceKey() {
        return new NamespacedKey(plugin, "used_rtp_once");
    }

    public RTPCommand(SimpleRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, getMessage("player-only"));
            return true;
        }

        // No permission
        if (!player.hasPermission("srtp.rtp") && !player.hasPermission("srtp.rtp.once")) {
            sendMessage(player, getMessage("no-permission"));
            return true;
        }

        // World filter: when enabled, player must be in one of the allowed worlds
        World world = resolveRTPWorld(player);
        if (world == null) {
            FileConfiguration cfg = plugin.getConfig();
            boolean filterEnabled = cfg.getBoolean("world-filter-enabled", false);
            if (filterEnabled) {
                List<String> allowed = cfg.getStringList("worlds");
                String worldList = (allowed != null && !allowed.isEmpty()) ? String.join(", ", allowed) : "";
                sendMessage(player, getMessage("world-not-allowed").replace("%worlds%", worldList));
            } else {
                sendMessage(player, getMessage("world-not-found"));
            }
            return true;
        }

        // Cost (Vault)
        FileConfiguration config = plugin.getConfig();
        boolean costEnabled = config.getBoolean("cost-enabled", false);
        double costAmount = config.getDouble("cost-amount", 0);
        if (costEnabled && costAmount > 0) {
            Object economy = plugin.getEconomyProvider();
            if (economy == null) {
                sendMessage(player, getMessage("cost-no-vault"));
                return true;
            }
            if (!VaultEconomyHelper.hasEnough(economy, player, costAmount)) {
                sendMessage(player, getMessage("cost-insufficient").replace("%cost%", String.valueOf(costAmount)));
                return true;
            }
        }

        // Has srtp.rtp (unlimited) or srtp.rtp.once
        boolean unlimited = player.hasPermission("srtp.rtp");
        if (!unlimited) {
            // Only has srtp.rtp.once: check if already used
            if (hasUsedRTPOnce(player)) {
                sendMessage(player, getMessage("used-once"));
                return true;
            }
        }

        int cooldownSeconds = plugin.getConfig().getInt("cooldown", 0);
        if (cooldownSeconds > 0) {
            Long endAt = cooldownEndByUuid.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (endAt != null && now < endAt) {
                long remaining = (endAt - now) / 1000;
                sendMessage(player, getMessage("cooldown").replace("%seconds%", String.valueOf(remaining)));
                return true;
            }
        }

        int radius = plugin.getConfig().getInt("radius", 1000);

        sendMessage(player, getMessage("teleporting"));

        Location target = findSafeLocation(world, radius);
        if (target == null) {
            sendMessage(player, getMessage("failed"));
            return true;
        }

        final double costToDeduct = costEnabled && costAmount > 0 ? costAmount : 0;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (costToDeduct > 0) {
                Object economy = plugin.getEconomyProvider();
                if (economy != null && !VaultEconomyHelper.withdraw(economy, player, costToDeduct)) {
                    sendMessage(player, getMessage("failed"));
                    return;
                }
            }
            player.teleport(target);
            sendMessage(player, getMessage("success"));

            if (cooldownSeconds > 0) {
                cooldownEndByUuid.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
            }
            if (!unlimited) {
                setUsedRTPOnce(player);
            }
        });

        return true;
    }

    /**
     * Resolves the destination world for RTP.
     * If world-filter-enabled is false, returns the player's current world (command allowed in any world).
     * If true, returns the player's current world only when it is in the allowed list; otherwise null.
     */
    private World resolveRTPWorld(Player player) {
        FileConfiguration config = plugin.getConfig();
        boolean filterEnabled = config.getBoolean("world-filter-enabled", false);
        if (!filterEnabled) {
            return player.getWorld();
        }
        List<String> worldNames = config.getStringList("worlds");
        if (worldNames == null || worldNames.isEmpty()) {
            return null;
        }
        String currentName = player.getWorld().getName();
        if (worldNames.contains(currentName)) {
            return player.getWorld();
        }
        return null;
    }

    /**
     * Finds a safe location within the radius. The player always spawns on top of a solid block
     * (feet in the air block above ground), never floating in the air.
     */
    private Location findSafeLocation(World world, int radius) {
        int minX = -radius;
        int maxX = radius;
        int minZ = -radius;
        int maxZ = radius;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            int highestY = world.getHighestBlockYAt(x, z);
            if (highestY <= world.getMinHeight()) {
                continue;
            }

            // Nether: never spawn on or above the roof (above Y 124)
            if (world.getEnvironment() == World.Environment.NETHER && (highestY + 1) > 124) {
                continue;
            }

            // Player at y = highestY + 1: feet in air above solid block, always on a block
            Location loc = new Location(world, x + 0.5, highestY + 1, z + 0.5);
            if (isSafeLocation(world, loc)) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Checks that the player spawns on a solid block: solid block under feet,
     * feet and head space clear (not inside blocks), no lava.
     */
    private boolean isSafeLocation(World world, Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material below = world.getBlockAt(x, y - 1, z).getType();

        // Must be on a solid block (not in the air)
        if (below.isAir() || !below.isSolid()) return false;
        if (below == Material.LAVA) return false;
        // Feet and head must be passable (not inside a block)
        if (feet.isSolid() || head.isSolid()) return false;
        if (feet == Material.LAVA || head == Material.LAVA) return false;
        // Avoid spawning above world max height
        if (y + 1 >= world.getMaxHeight()) return false;

        return true;
    }

    private boolean hasUsedRTPOnce(Player player) {
        return Boolean.TRUE.equals(
                player.getPersistentDataContainer().get(getUsedRTPOnceKey(), PersistentDataType.BOOLEAN));
    }

    private void setUsedRTPOnce(Player player) {
        player.getPersistentDataContainer().set(getUsedRTPOnceKey(), PersistentDataType.BOOLEAN, true);
    }

    private String getPrefix() {
        return plugin.getConfig().getString("prefix", "");
    }

    private String getMessage(String key) {
        return plugin.getConfig().getString("messages." + key, "&7[" + key + "]");
    }

    private void sendMessage(CommandSender sender, String raw) {
        String prefix = getPrefix();
        String text = (prefix != null && !prefix.isEmpty()) ? prefix + raw : raw;
        text = text.replace('&', '\u00A7');
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);
        sender.sendMessage(component);
    }
}

package com.simpleplugins.simplertp.command;

import com.simpleplugins.simplertp.SimpleRTP;
import com.simpleplugins.simplertp.economy.VaultEconomyHelper;
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
        Player target;
        boolean selfTeleport = args.length == 0;

        if (selfTeleport) {
            if (!(sender instanceof Player player)) {
                sendMessage(sender, "player-only");
                return true;
            }
            target = player;
        } else {
            // /rtp <player>
            if (sender instanceof Player player && !player.hasPermission("srtp.rtp.others")) {
                sendMessage(player, "no-permission-others");
                return true;
            }

            Player found = Bukkit.getPlayer(args[0]);
            if (found == null) {
                sendMessage(sender, "player-not-found");
                return true;
            }
            target = found;
        }

        Player player = target;

        // No permission on target
        if (!player.hasPermission("srtp.rtp") && !player.hasPermission("srtp.rtp.once")) {
            sendMessage(player, "no-permission");
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
                sendMessage(player, "world-not-allowed", msg -> msg.replace("%worlds%", worldList));
            } else {
                sendMessage(player, "world-not-found");
            }
            return true;
        }

        // Cost (Vault) - only applied when a player teleports themselves, NOT when teleporting others
        FileConfiguration config = plugin.getConfig();
        boolean costEnabled = config.getBoolean("cost-enabled", false);
        double costAmount = config.getDouble("cost-amount", 0);
        if (selfTeleport && costEnabled && costAmount > 0) {
            Object economy = plugin.getEconomyProvider();
            if (economy == null) {
                sendMessage(player, "cost-no-vault");
                return true;
            }
            if (!VaultEconomyHelper.hasEnough(economy, player, costAmount)) {
                sendMessage(player, "cost-insufficient", msg -> msg.replace("%cost%", String.valueOf(costAmount)));
                return true;
            }
        }

        // Has srtp.rtp (unlimited) or srtp.rtp.once
        boolean unlimited = player.hasPermission("srtp.rtp");
        if (!unlimited) {
            // Only has srtp.rtp.once: check if already used
            if (hasUsedRTPOnce(player)) {
                sendMessage(player, "used-once");
                return true;
            }
        }

        int cooldownSeconds = plugin.getConfig().getInt("cooldown", 0);
        // Cooldown only applies when a player teleports themselves
        if (selfTeleport && cooldownSeconds > 0) {
            Long endAt = cooldownEndByUuid.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (endAt != null && now < endAt) {
                long remaining = (endAt - now) / 1000;
                sendMessage(player, "cooldown", msg -> msg.replace("%seconds%", String.valueOf(remaining)));
                return true;
            }
        }

        int radius = plugin.getConfig().getInt("radius", 1000);

        sendMessage(player, "teleporting");

        Location targetLocation = findSafeLocation(player, world, radius);
        if (targetLocation == null) {
            sendMessage(player, "failed");
            return true;
        }

        final double costToDeduct = (selfTeleport && costEnabled && costAmount > 0) ? costAmount : 0;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (costToDeduct > 0) {
                Object economy = plugin.getEconomyProvider();
                if (economy != null && !VaultEconomyHelper.withdraw(economy, player, costToDeduct)) {
                    sendMessage(player, "failed");
                    return;
                }
            }
            player.teleport(targetLocation);

            playTeleportSound(player);
            sendMessage(player, "success");

            // Only set cooldown when the player teleports themselves
            if (selfTeleport && cooldownSeconds > 0) {
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
    private Location findSafeLocation(Player player, World world, int radius) {
        int centerX;
        int centerZ;

        String from = plugin.getConfig().getString("rtp-from", "center");
        if ("player".equalsIgnoreCase(from)) {
            Location loc = player.getLocation();
            centerX = loc.getBlockX();
            centerZ = loc.getBlockZ();
        } else {
            centerX = 0;
            centerZ = 0;
        }

        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

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

    private void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, msg -> msg);
    }

    private void sendMessage(CommandSender sender, String key, java.util.function.UnaryOperator<String> transformer) {
        String raw = getMessage(key);
        raw = transformer.apply(raw);

        boolean useActionBar = shouldUseActionBar(sender, key);

        String prefix = getPrefix();
        String text = raw;
        // Only add prefix for chat messages; action bar messages are sent without prefix
        if (!useActionBar && prefix != null && !prefix.isEmpty()) {
            text = prefix + raw;
        }

        text = text.replace('&', '\u00A7');
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);

        if (useActionBar && sender instanceof Player player) {
            player.sendActionBar(component);
        } else {
            sender.sendMessage(component);
        }
    }

    private boolean shouldUseActionBar(CommandSender sender, String key) {
        if (!(sender instanceof Player)) {
            return false;
        }
        String mode = plugin.getConfig().getString("message-delivery", "chat");
        if (!"action_bar".equalsIgnoreCase(mode) && !"action-bar".equalsIgnoreCase(mode)) {
            return false;
        }
        // Only these keys are eligible for action bar
        return key.equals("success")
                || key.equals("failed")
                || key.equals("used-once")
                || key.equals("cooldown");
    }

    private void playTeleportSound(Player player) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("makesound", false)) {
            return;
        }
        String soundKey = config.getString("sound", "entity.enderman.teleport");
        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }

        // Convert a namespaced id like "entity.enderman.teleport" to a Bukkit Sound enum name.
        // Example: "entity.enderman.teleport" -> "ENTITY_ENDERMAN_TELEPORT"
        String enumName = soundKey.toUpperCase(Locale.ROOT).replace('.', '_');
        try {
            Sound sound = Sound.valueOf(enumName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ex) {
            // Invalid or unsupported sound; ignore silently.
        }
    }
}


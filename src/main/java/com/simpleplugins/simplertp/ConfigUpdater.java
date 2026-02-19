package com.simpleplugins.simplertp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;

/**
 * Merges the plugin's default config (from the jar) with the existing config.yml
 * on disk. New or renamed keys from the default are added; existing user values
 * are never overwritten. Only writes to disk when at least one key was added,
 * to minimize file writes.
 * <p>
 * Supports nested sections and lists. Compatible with Bukkit/Spigot
 * FileConfiguration API. Safe to call on every load and reload.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * Loads the default config from the plugin jar, merges it with the current
     * config file (adding only missing keys), and optionally saves and
     * reloads. Existing user values are never changed.
     *
     * @param plugin the plugin
     * @return true if config.yml was updated (new keys added and file written)
     */
    public static boolean mergeWithDefaults(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return false;
        }

        FileConfiguration defaultConfig = loadDefaultFromJar(plugin);
        if (defaultConfig == null) {
            plugin.getLogger().warning("Could not load default config from jar; skipping merge.");
            return false;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        FileConfiguration merged = copySection(currentConfig.getRoot());
        boolean modified = addMissingDefaults(merged, defaultConfig, currentConfig, "");

        if (!modified) {
            return false;
        }

        try {
            merged.save(configFile);
            plugin.reloadConfig();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save merged config", e);
            return false;
        }
    }

    /**
     * Recursively adds keys from default into merged where they are missing in
     * current. Merged is built from current first (all user keys preserved).
     * Returns true if any key was added from default.
     */
    private static boolean addMissingDefaults(
            ConfigurationSection merged,
            ConfigurationSection defaultSection,
            ConfigurationSection currentSection,
            String pathPrefix
    ) {
        boolean modified = false;
        Set<String> keys = defaultSection.getKeys(false);

        for (String key : keys) {
            String path = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            if (defaultSection.isConfigurationSection(key)) {
                ConfigurationSection defaultChild = defaultSection.getConfigurationSection(key);
                ConfigurationSection currentChild = currentSection.getConfigurationSection(key);
                ConfigurationSection mergedChild = merged.getConfigurationSection(key);
                if (mergedChild == null) {
                    mergedChild = merged.createSection(key);
                }
                if (currentChild == null) {
                    currentChild = emptySection();
                }
                if (addMissingDefaults(mergedChild, defaultChild, currentChild, "")) {
                    modified = true;
                }
                continue;
            }

            if (!currentSection.contains(key)) {
                merged.set(key, defaultSection.get(key));
                modified = true;
            }
        }

        return modified;
    }

    /**
     * Deep-copies a configuration section so we can modify the copy without
     * altering the original. Used to clone current config as the base for merge.
     */
    private static FileConfiguration copySection(ConfigurationSection section) {
        FileConfiguration out = new YamlConfiguration();
        if (section == null) {
            return out;
        }
        for (String path : section.getKeys(true)) {
            if (section.isConfigurationSection(path)) {
                continue;
            }
            Object value = section.get(path);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            out.set(path, value);
        }
        return out;
    }

    private static ConfigurationSection emptySection() {
        return new YamlConfiguration();
    }

    /**
     * Loads the default config from the plugin's config.yml resource in the jar.
     */
    private static FileConfiguration loadDefaultFromJar(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load default config from jar", e);
            return null;
        }
    }
}

package com.simpleplugins.simplertp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Modrinth for newer plugin versions and notifies console and admins.
 */
public final class UpdateChecker {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/simple-rtp%2B/version";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/simple-rtp%2B";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final String currentVersion;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Runs the update check asynchronously. If a newer version exists, notifies console and admins.
     */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String latestVersion = fetchLatestVersion();
            if (latestVersion != null && isNewer(latestVersion, currentVersion)) {
                Bukkit.getScheduler().runTask(plugin, this::sendUpdateNotification);
            }
        });
    }

    private String fetchLatestVersion() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            Matcher matcher = VERSION_PATTERN.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isNewer(String remote, String current) {
        int[] r = parseVersion(remote);
        int[] c = parseVersion(current);
        for (int i = 0; i < Math.max(r.length, c.length); i++) {
            int rVal = i < r.length ? r[i] : 0;
            int cVal = i < c.length ? c[i] : 0;
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }

    private int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] result = new int[Math.min(parts.length, 3)];
        for (int i = 0; i < result.length; i++) {
            String segment = parts[i].replaceAll("[^0-9].*", "");
            try {
                result[i] = segment.isEmpty() ? 0 : Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private void sendUpdateNotification() {
        Component line1 = Component.text("> A new version of Simple RTP is available").color(NamedTextColor.YELLOW);
        Component line2 = Component.text("> ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("Click here to download it")
                        .color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.openUrl(MODRINTH_URL)));

        Bukkit.getConsoleSender().sendMessage(line1);
        Bukkit.getConsoleSender().sendMessage(line2);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("srtp.reload")) {
                p.sendMessage(line1);
                p.sendMessage(line2);
            }
        }
    }
}

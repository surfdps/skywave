package org.wxter.skywave.client.waypoints;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.wxter.skywave.client.HypixelSkyblockContext;
import org.wxter.skywave.client.SkywaveClient;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WaypointChatParser {
    private WaypointChatParser() {}

    private static final int[] COLOR_PALETTE = {
            0xFFFFFFFF, 0xFFBFBFBF, 0xFF7F7F7F, 0xFF000000,
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
            0xFF00AA00, 0xFF00AAAA, 0xFF55FFFF, 0xFF5555FF,
            0xFFAA00AA, 0xFFFF55FF, 0xFFFFAAAA, 0xFFAA5500
    };

    private static final Pattern LABELED = Pattern.compile(
            "(?i)\\bx\\s*[:=]\\s*(-?\\d{1,6})\\b.*?\\by\\s*[:=]\\s*(-?\\d{1,6})\\b.*?\\bz\\s*[:=]\\s*(-?\\d{1,6})\\b"
    );
    private static final Pattern TRIPLE = Pattern.compile(
            "(?<!\\d)(-?\\d{1,6})\\s*(?:,\\s*|\\s+)(-?\\d{1,6})\\s*(?:,\\s*|\\s+)(-?\\d{1,6})(?!\\d)"
    );

    private static final long REQUEST_TTL_MS = 60_000L;
    private static final Map<String, PendingRequest> PENDING = new ConcurrentHashMap<>();

    private record PendingRequest(int x, int y, int z, String name, int colorArgb, long createdAtMs) {}

    public static void init() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            try {
                handleChatMessage(message);
            } catch (Throwable t) {
                // Never break chat. Log via normal logger path.
                org.wxter.skywave.ModConstants.LOGGER.error("Waypoint chat parsing failed", t);
            }
        });
    }

    public static boolean accept(String requestId) {
        PendingRequest req = getAndRemoveValidRequest(requestId);
        if (req == null) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return false;

        String dim = client.world.getRegistryKey().getValue().toString();
        CustomWaypoints.addWaypoint(req.name, req.x, req.y, req.z, req.colorArgb, dim);
        return true;
    }

    public static void deny(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        PENDING.remove(requestId);
    }

    private static void handleChatMessage(@Nullable Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        SkywaveConfig.WaypointsConfig cfg = SkywaveConfig.get().waypoints;
        if (cfg == null || !cfg.chatParsingEnabled) return;
        if (!HypixelSkyblockContext.isOnHypixelSkyblock(client)) return;

        String plain = message == null ? "" : message.getString();
        if (plain == null || plain.isBlank()) return;

        SkywaveConfig.WaypointChatParseChannel channel = detectChannel(plain);
        if (channel == null) return;
        if (cfg.chatParseChannel != channel) return;

        Parsed parsed = parseCoordinates(plain);
        if (parsed == null) return;

        cleanupExpired();
        String id = UUID.randomUUID().toString().substring(0, 8);
        int color = pickRandomColor();
        PENDING.put(id, new PendingRequest(parsed.x, parsed.y, parsed.z, parsed.name, color, System.currentTimeMillis()));

        SkywaveClient.sendChat(SkywaveClient.prefix().append(Text.literal(
                "Coordinates fixed in chat, do you want to create a new waypoint?"
        ).formatted(Formatting.GRAY)));

        MutableText yes = Text.literal("[Yes]").setStyle(Style.EMPTY
                .withColor(Formatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/sw waypoints chat yes " + id))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Create waypoint").formatted(Formatting.GRAY)))
        );

        MutableText no = Text.literal("[No]").setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/sw waypoints chat no " + id))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Dismiss").formatted(Formatting.GRAY)))
        );

        SkywaveClient.sendChat(Text.empty()
                .append(Text.literal("  ").formatted(Formatting.DARK_GRAY))
                .append(yes)
                .append(Text.literal(" / ").formatted(Formatting.DARK_GRAY))
                .append(no)
        );
    }

    private record Parsed(int x, int y, int z, String name) {}

    @Nullable
    private static Parsed parseCoordinates(String plain) {
        if (plain == null) return null;

        int pipeIdx = plain.indexOf('|');
        String name = "Waypoint";
        if (pipeIdx >= 0) {
            String tail = plain.substring(pipeIdx + 1).trim();
            if (!tail.isBlank()) name = tail;
        }

        Matcher m = LABELED.matcher(plain);
        if (m.find()) {
            Parsed parsed = makeParsed(m.group(1), m.group(2), m.group(3), name);
            if (parsed != null) return parsed;
        }

        m = TRIPLE.matcher(plain);
        if (m.find()) {
            Parsed parsed = makeParsed(m.group(1), m.group(2), m.group(3), name);
            if (parsed != null) return parsed;
        }

        return null;
    }

    @Nullable
    private static Parsed makeParsed(String xs, String ys, String zs, String name) {
        try {
            int x = Integer.parseInt(xs);
            int y = Integer.parseInt(ys);
            int z = Integer.parseInt(zs);
            if (!isReasonable(x, y, z)) return null;
            String n = name == null || name.isBlank() ? "Waypoint" : name.trim();
            return new Parsed(x, y, z, n);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isReasonable(int x, int y, int z) {
        if (y < -256 || y > 512) return false;
        return Math.abs(x) <= 30_000 && Math.abs(z) <= 30_000;
    }

    @Nullable
    private static SkywaveConfig.WaypointChatParseChannel detectChannel(String plain) {
        String s = plain.stripLeading();
        if (s.regionMatches(true, 0, "Party >", 0, "Party >".length())) {
            return SkywaveConfig.WaypointChatParseChannel.PARTY;
        }

        // Treat other "X >" channels as not-ALL (guild/officer/co-op/etc.)
        int sep = s.indexOf(" >");
        if (sep >= 0 && sep <= 12) {
            return null;
        }

        return SkywaveConfig.WaypointChatParseChannel.ALL;
    }

    private static int pickRandomColor() {
        int idx = (int) (System.nanoTime() % COLOR_PALETTE.length);
        if (idx < 0) idx = -idx;
        return COLOR_PALETTE[idx];
    }

    @Nullable
    private static PendingRequest getAndRemoveValidRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) return null;
        PendingRequest req = PENDING.remove(requestId);
        if (req == null) return null;
        long now = System.currentTimeMillis();
        if ((now - req.createdAtMs) > REQUEST_TTL_MS) return null;
        return req;
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> (now - e.getValue().createdAtMs) > REQUEST_TTL_MS);
    }
}


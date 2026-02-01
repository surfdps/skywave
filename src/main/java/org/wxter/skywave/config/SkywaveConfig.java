package org.wxter.skywave.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkywaveConfig {

    public enum RainReminderType {
        CHAT,      // отправлять в чат
        ONSCREEN   // отображать крупным текстом сверху
    }

    /** Display mode for various trackers (Total = persistent total, Session = current run only). */
    public enum DisplayMode {
        TOTAL,
        SESSION
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skywave.json");

    private static SkywaveConfig INSTANCE;

    // ===== НАСТРОЙКИ =====
    public boolean rainReminderEnabled = true;
    public boolean rainReminderSound = true;

    public RainReminderType rainReminderType = RainReminderType.CHAT;

    /** Enable mob highlight by nametag (works with custom names, scoreboard team names, and Hypixel-style armor stand name tags). */
    public boolean mobHighlightEnabled = true;

    /** Nametags to highlight (e.g. "Night Squid", "Golden Goblin"). Matches entity display name from any source. */
    public List<String> mobHighlightNametags = new ArrayList<>(List.of("Night Squid"));

    /** ARGB (int) — highlight outline color (default: deep sky blue 0xFF00BFFF). */
    public int mobHighlightColor = 0xFF00BFFF;
    // ======================

    // ===== HUNTING PROFIT TRACKER CONFIG =====
    public HuntingConfig hunting = new HuntingConfig();

    public static class HuntingConfig {
        /** Enable the Hunting Profit Tracker (HUD + parsing). */
        public boolean profitTrackerEnabled = false;

        /** Show session timer on HUD. */
        public boolean showTimer = true;

        /** Which value HUD shows by default (Total: persistent, Session: current counting session). */
        public DisplayMode displayMode = DisplayMode.SESSION;

        /**
         * List of regex patterns (Java regex) used to parse chat messages and extract shard counts.
         * These are user-editable — Hypixel strings sometimes change, so tweak them if needed.
         * Capture group 1 should contain the numeric count when possible.
         */
        public List<String> chatPatterns = new ArrayList<>(List.of(
                // flexible defaults — adjust to exact Hypixel chat lines if necessary
                // Generic shard gain
                "(?:You found|You received|You obtained|You got) .*Shard.* x?(\\d+)",

                // Loot Share assist message
                "LOOT SHARE You received (\\d+) .+? Shards",
                "LOOT SHARE You received (\\d+) .+? Shard",

                // Basic shard message
                "You caught x?(\\d+) .+? Shards",
                "You caught ?(\\d+) .+? Shard",

                // Fallback generic
                "(.+Shard.+) x?(\\d+)",
                "Picked up (\\d+)x? (?:.*Shard.*)"
        ));

        /** HUD position (pixels, scaled screen coords). */
        public int hudX = 8;
        public int hudY = 40;

        /** Persistent total of shards (saved in the config file). */
        public long totalShards = 0L;
    }
    // ==========================================

    public static SkywaveConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                INSTANCE = GSON.fromJson(reader, SkywaveConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = new SkywaveConfig();
            }
        } else {
            INSTANCE = new SkywaveConfig();
            save();
        }
    }

    public static void save() {
        try {
            Path dir = FILE.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
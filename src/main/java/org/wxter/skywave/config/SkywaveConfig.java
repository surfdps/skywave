// src/main/java/org/wxter/skywave/config/SkywaveConfig.java
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkywaveConfig {

    public enum RainReminderType {
        CHAT,
        ONSCREEN
    }

    public enum DisplayMode {
        TOTAL,
        SESSION
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skywave.json");

    private static SkywaveConfig INSTANCE;

    // ===== GENERAL =====
    public boolean rainReminderEnabled = true;
    public boolean rainReminderSound = true;
    public RainReminderType rainReminderType = RainReminderType.CHAT;

    public boolean mobHighlightEnabled = true;
    public List<String> mobHighlightNametags = new ArrayList<>(List.of("Night Squid"));
    public int mobHighlightColor = 0xFF00BFFF;
    // ===================

    // ===== HUNTING TRACKER =====
    public HuntingConfig hunting = new HuntingConfig();

    public static class HuntingConfig {
        public boolean profitTrackerEnabled = false;
        public boolean showTimer = true;
        public DisplayMode displayMode = DisplayMode.SESSION;

        public List<String> chatPatterns = new ArrayList<>(List.of(
                // generic
                "(?:You found|You received|You obtained|You got) .*Shard.* x?(\\d+)",
                // loot share
                "LOOT SHARE You received (\\d+) .+? Shards",
                "LOOT SHARE You received (\\d+) .+? Shard",
                // caught messages (handles x2 and 2)
                "You caught x?(\\d+) .+? Shards",
                "You caught ?(\\d+) .+? Shard",
                // fallback
                "(.+Shard.+) x?(\\d+)",
                "Picked up (\\d+)x? (?:.*Shard.*)"
        ));

        public int hudX = 8;
        public int hudY = 40;

        /** Legacy aggregated single total */
        public long totalShards = 0L;

        /** Optional per-item totals persisted */
        public Map<String, Long> huntingTotals = new HashMap<>();

        /** Show unit prices on HUD (requires hypixelApiKey) */
        public boolean showUnitPrices = true;
    }
    // ============================

    // ===== Hypixel API =====
    public String hypixelApiKey = "";
    /** minutes */
    public int bazaarRefreshMinutes = 5;
    // =======================

    // ===== HUD Positions =====
    /** Centered if negative; absolute screen x position otherwise. */
    public int rainReminderHudX = -1;
    public int rainReminderHudY = 80;
    // =========================

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
            if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

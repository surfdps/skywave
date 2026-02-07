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
import java.util.UUID;

public class SkywaveConfig {

    public enum RainReminderType {
        CHAT,
        ONSCREEN
    }

    public enum DisplayMode {
        TOTAL,
        SESSION
    }

    public enum BazaarPriceMode {
        BUY_OFFER,
        SELL_OFFER
    }

    public enum HuntingSortMode {
        PROFIT,
        RARITY
    }

    public enum WaypointChatParseChannel {
        ALL,
        PARTY
    }

    public enum NightSquidAlertSound {
        ANVIL,
        BELL,
        PLING,
        ORB
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skywave.json");

    private static SkywaveConfig INSTANCE;

    // ===== GENERAL =====
    public boolean rainReminderEnabled = true;
    public boolean rainReminderSound = true;
    public RainReminderType rainReminderType = RainReminderType.CHAT;
    public NightSquidAlertSound nightSquidAlertSound = NightSquidAlertSound.ANVIL;
    public boolean muteEnderDragonSounds = false;

    public boolean mobHighlightEnabled = true;
    public List<String> mobHighlightNametags = new ArrayList<>(List.of("Night Squid"));
    public int mobHighlightColor = 0xFF00BFFF;

    /** Draw background panels behind HUD trackers (applies to all trackers). */
    public boolean hudBackgroundPanelsEnabled = true;
    // ===================

    // ===== HUNTING TRACKER =====
    public HuntingConfig hunting = new HuntingConfig();

    public static class HuntingConfig {
        public boolean profitTrackerEnabled = false;
        public boolean showTimer = true;
        /**
         * If enabled, Lootshare shards are shown as separate lines (tagged "LS").
         * If disabled, Lootshare shards are merged into the main shard totals.
         */
        public boolean showLootshareShards = true;
        /** Count shards from "You sent ... to your Hunting Box" messages (can skew Coins/h). */
        public boolean countSentToHuntingBox = false;
        public HuntingSortMode sortMode = HuntingSortMode.PROFIT;
        public DisplayMode displayMode = DisplayMode.SESSION;
        public BazaarPriceMode bazaarPriceMode = BazaarPriceMode.BUY_OFFER;

        public List<String> chatPatterns = new ArrayList<>(List.of(
                // generic
                "(?:You found|You received|You obtained|You got) .*Shard.* x?(\\d+)",
                // loot share
                "LOOT\\s*SHARE You received (\\d+) .+? Shards",
                "LOOT\\s*SHARE You received (\\d+) .+? Shard",
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

        /** Last-seen rarity color for shard base names (persisted across restarts). */
        public Map<String, Integer> shardRarityRgb = new HashMap<>();
        /** Last-seen rarity weight for shard base names (persisted across restarts). */
        public Map<String, Integer> shardRarityWeight = new HashMap<>();

        /** Show unit prices on HUD (requires hypixelApiKey) */
        public boolean showUnitPrices = true;
    }
    // ============================

    // ===== Hypixel API =====
    public String hypixelApiKey = "8c0960f8-3b18-41ca-82cb-7c55b4ac67b7";
    /** minutes */
    public int bazaarRefreshMinutes = 1;
    // =======================

    // ===== CRYSTAL NUCLEUS =====
    public CrystalNucleusConfig crystalNucleus = new CrystalNucleusConfig();

    public static class CrystalNucleusConfig {
        public boolean jungleSkipWaypointsEnabled = false;
    }
    // ===========================

    // ===== HUD Positions =====
    /** Centered if negative; absolute screen x position otherwise. */
    public int rainReminderHudX = -1;
    public int rainReminderHudY = 80;
    // =========================

    // ===== WAYPOINTS =====
    public WaypointsConfig waypoints = new WaypointsConfig();

    public static class WaypointsConfig {
        public boolean enabled = false;
        public boolean showDistance = true;
        public boolean highlightBlockInFov = true;
        public boolean onlySameDimension = true;

        public boolean chatParsingEnabled = false;
        public WaypointChatParseChannel chatParseChannel = WaypointChatParseChannel.ALL;

        public String activePresetId = "default";
        public List<WaypointPreset> presets = new ArrayList<>(List.of(WaypointPreset.defaultPreset()));

        private void fixup() {
            if (chatParseChannel == null) chatParseChannel = WaypointChatParseChannel.ALL;

            if (presets == null) presets = new ArrayList<>();
            if (presets.isEmpty()) presets.add(WaypointPreset.defaultPreset());

            for (WaypointPreset preset : presets) {
                if (preset == null) continue;
                if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
                if (preset.name == null || preset.name.isBlank()) preset.name = "Preset";
                if (preset.waypoints == null) preset.waypoints = new ArrayList<>();
                for (WaypointEntry wp : preset.waypoints) {
                    if (wp == null) continue;
                    if (wp.id == null || wp.id.isBlank()) wp.id = UUID.randomUUID().toString();
                    if (wp.name == null || wp.name.isBlank()) wp.name = "Waypoint";
                }
            }

            if (activePresetId == null || activePresetId.isBlank()) {
                activePresetId = presets.getFirst().id;
            }

            boolean foundActive = false;
            for (WaypointPreset preset : presets) {
                if (preset != null && activePresetId.equals(preset.id)) {
                    foundActive = true;
                    break;
                }
            }
            if (!foundActive) activePresetId = presets.getFirst().id;
        }
    }

    public static class WaypointPreset {
        public String id = "";
        public String name = "Default";
        public List<WaypointEntry> waypoints = new ArrayList<>();

        public static WaypointPreset defaultPreset() {
            WaypointPreset preset = new WaypointPreset();
            preset.id = "default";
            preset.name = "Default";
            return preset;
        }
    }

    public static class WaypointEntry {
        public String id = "";
        public String name = "Waypoint";
        public int x = 0;
        public int y = 0;
        public int z = 0;
        /** ARGB */
        public int color = 0xFF00BFFF;
        /** Registry id string, e.g. "minecraft:overworld" */
        public String dimension = "minecraft:overworld";
        public boolean enabled = true;
    }
    // ==================

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

        if (INSTANCE == null) INSTANCE = new SkywaveConfig();
        INSTANCE.fixup();
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

    private void fixup() {
        if (mobHighlightNametags == null) mobHighlightNametags = new ArrayList<>();

        if (nightSquidAlertSound == null) nightSquidAlertSound = NightSquidAlertSound.ANVIL;

        if (hunting == null) hunting = new HuntingConfig();
        if (hunting.chatPatterns == null) hunting.chatPatterns = new ArrayList<>();
        if (hunting.huntingTotals == null) hunting.huntingTotals = new HashMap<>();
        if (hunting.shardRarityRgb == null) hunting.shardRarityRgb = new HashMap<>();
        if (hunting.shardRarityWeight == null) hunting.shardRarityWeight = new HashMap<>();

        if (crystalNucleus == null) crystalNucleus = new CrystalNucleusConfig();

        if (waypoints == null) waypoints = new WaypointsConfig();
        waypoints.fixup();
    }
}

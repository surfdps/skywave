package org.wxter.skywave.client.waypoints;

import org.wxter.skywave.config.SkywaveConfig;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CustomWaypoints {
    private CustomWaypoints() {}

    public static SkywaveConfig.WaypointsConfig config() {
        return SkywaveConfig.get().waypoints;
    }

    public static List<SkywaveConfig.WaypointPreset> presets() {
        return config().presets;
    }

    public static SkywaveConfig.WaypointPreset activePreset() {
        SkywaveConfig.WaypointsConfig cfg = config();
        SkywaveConfig.WaypointPreset preset = getPresetById(cfg.activePresetId);
        if (preset != null) return preset;

        if (cfg.presets == null || cfg.presets.isEmpty()) {
            cfg.presets = new java.util.ArrayList<>(List.of(SkywaveConfig.WaypointPreset.defaultPreset()));
        }

        SkywaveConfig.WaypointPreset fallback = cfg.presets.getFirst();
        cfg.activePresetId = fallback.id;
        SkywaveConfig.save();
        return fallback;
    }

    public static List<SkywaveConfig.WaypointEntry> activeWaypoints() {
        return activePreset().waypoints;
    }

    public static SkywaveConfig.WaypointPreset getPresetById(String id) {
        if (id == null || id.isBlank()) return null;
        List<SkywaveConfig.WaypointPreset> presets = presets();
        if (presets == null) return null;
        for (SkywaveConfig.WaypointPreset preset : presets) {
            if (preset == null) continue;
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    public static void setActivePreset(String presetId) {
        SkywaveConfig.WaypointsConfig cfg = config();
        if (getPresetById(presetId) == null) return;
        cfg.activePresetId = presetId;
        SkywaveConfig.save();
    }

    public static String createPreset(String name) {
        SkywaveConfig.WaypointsConfig cfg = config();
        if (cfg.presets == null) cfg.presets = new java.util.ArrayList<>();

        SkywaveConfig.WaypointPreset preset = new SkywaveConfig.WaypointPreset();
        preset.id = UUID.randomUUID().toString();
        preset.name = sanitizeName(name, "Preset");
        preset.waypoints = new java.util.ArrayList<>();

        cfg.presets.add(preset);
        cfg.activePresetId = preset.id;
        SkywaveConfig.save();
        return preset.id;
    }

    public static void deletePreset(String presetId) {
        SkywaveConfig.WaypointsConfig cfg = config();
        if (cfg.presets == null || cfg.presets.size() <= 1) return;
        if (presetId == null || presetId.isBlank()) return;

        cfg.presets.removeIf(p -> p != null && presetId.equals(p.id));
        if (cfg.presets.isEmpty()) cfg.presets.add(SkywaveConfig.WaypointPreset.defaultPreset());

        if (presetId.equals(cfg.activePresetId)) {
            cfg.activePresetId = cfg.presets.getFirst().id;
        }

        SkywaveConfig.save();
    }

    public static void renameActivePreset(String newName) {
        SkywaveConfig.WaypointPreset preset = activePreset();
        preset.name = sanitizeName(newName, preset.name);
        SkywaveConfig.save();
    }

    public static SkywaveConfig.WaypointEntry addWaypoint(String name, int x, int y, int z, int color, String dimensionId) {
        SkywaveConfig.WaypointEntry wp = new SkywaveConfig.WaypointEntry();
        wp.id = UUID.randomUUID().toString();
        wp.name = sanitizeName(name, "Waypoint");
        wp.x = x;
        wp.y = y;
        wp.z = z;
        wp.color = color;
        if (dimensionId != null && !dimensionId.isBlank()) {
            wp.dimension = dimensionId;
        }
        wp.enabled = true;
        activeWaypoints().add(wp);
        SkywaveConfig.save();
        return wp;
    }

    public static boolean updateWaypoint(String waypointId, String name, int x, int y, int z, int color, String dimensionId, boolean enabled) {
        SkywaveConfig.WaypointEntry wp = getWaypointById(waypointId);
        if (wp == null) return false;
        wp.name = sanitizeName(name, wp.name);
        wp.x = x;
        wp.y = y;
        wp.z = z;
        wp.color = color;
        if (dimensionId != null && !dimensionId.isBlank()) {
            wp.dimension = dimensionId;
        }
        wp.enabled = enabled;
        SkywaveConfig.save();
        return true;
    }

    public static void deleteWaypoint(String waypointId) {
        if (waypointId == null || waypointId.isBlank()) return;
        activeWaypoints().removeIf(wp -> wp != null && waypointId.equals(wp.id));
        SkywaveConfig.save();
    }

    public static boolean setWaypointEnabled(String waypointId, boolean enabled) {
        SkywaveConfig.WaypointEntry wp = getWaypointById(waypointId);
        if (wp == null) return false;
        wp.enabled = enabled;
        SkywaveConfig.save();
        return true;
    }

    public static SkywaveConfig.WaypointEntry getWaypointById(String waypointId) {
        if (waypointId == null || waypointId.isBlank()) return null;
        for (SkywaveConfig.WaypointEntry wp : activeWaypoints()) {
            if (wp == null) continue;
            if (waypointId.equals(wp.id)) return wp;
        }
        return null;
    }

    private static String sanitizeName(String name, String fallback) {
        String v = name == null ? "" : name.trim();
        if (v.isEmpty()) return fallback;
        v = v.replaceAll("\\s+", " ");
        if (v.length() > 48) v = v.substring(0, 48);
        return v;
    }

    public static int parseColor(String input, int fallbackArgb) {
        if (input == null) return fallbackArgb;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x")) s = s.substring(2);
        if (s.length() == 6) {
            try {
                int rgb = Integer.parseUnsignedInt(s, 16);
                return 0xFF000000 | rgb;
            } catch (NumberFormatException ignored) {
                return fallbackArgb;
            }
        }
        if (s.length() == 8) {
            try {
                return (int) Long.parseUnsignedLong(s, 16);
            } catch (NumberFormatException ignored) {
                return fallbackArgb;
            }
        }
        return fallbackArgb;
    }

    public static String formatColorHex(int argb) {
        int rgb = argb & 0xFFFFFF;
        return String.format(Locale.ROOT, "#%06X", rgb);
    }
}

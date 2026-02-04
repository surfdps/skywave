package org.wxter.skywave.client.waypoints;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.wxter.skywave.ModConstants;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JungleSkipWaypoints {
    private JungleSkipWaypoints() {}

    private static final List<SkywaveConfig.WaypointEntry> TEMP_WAYPOINTS = new ArrayList<>();
    private static int lastWorldIdentity = 0;
    private static String lastDimensionId = null;

    public static void tick(MinecraftClient client) {
        int worldIdentity = (client == null || client.world == null) ? 0 : System.identityHashCode(client.world);
        if (worldIdentity == 0) {
            TEMP_WAYPOINTS.clear();
            lastWorldIdentity = 0;
            lastDimensionId = null;
            return;
        }

        String dimensionId = client.world.getRegistryKey().getValue().toString();
        if (lastWorldIdentity != 0 && worldIdentity != lastWorldIdentity) {
            TEMP_WAYPOINTS.clear();
        } else if (lastDimensionId != null && !lastDimensionId.equals(dimensionId)) {
            TEMP_WAYPOINTS.clear();
        }
        lastWorldIdentity = worldIdentity;
        lastDimensionId = dimensionId;
    }

    public static List<SkywaveConfig.WaypointEntry> getWaypoints() {
        return TEMP_WAYPOINTS;
    }

    public static void clear() {
        TEMP_WAYPOINTS.clear();
    }

    public static boolean createFromPlayerPosition(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return false;

        try {
            BlockPos pos = client.player.getBlockPos();
            int baseX = pos.getX();
            int baseY = pos.getY();
            int baseZ = pos.getZ();
            String dim = client.world.getRegistryKey().getValue().toString();

            TEMP_WAYPOINTS.clear();
            TEMP_WAYPOINTS.add(create("Skip Entry", baseX + 29, baseY - 33, baseZ + 48, 0xFFFFAA00, dim));
            TEMP_WAYPOINTS.add(create("Start Dig", baseX + 29, baseY - 13, baseZ + 48, 0xFF55FF55, dim));
            return true;
        } catch (Throwable t) {
            ModConstants.LOGGER.error("Failed to create Jungle Skip waypoints", t);
            TEMP_WAYPOINTS.clear();
            return false;
        }
    }

    private static SkywaveConfig.WaypointEntry create(String name, int x, int y, int z, int color, String dimension) {
        SkywaveConfig.WaypointEntry wp = new SkywaveConfig.WaypointEntry();
        wp.id = UUID.randomUUID().toString();
        wp.name = name;
        wp.x = x;
        wp.y = y;
        wp.z = z;
        wp.color = color;
        wp.dimension = dimension;
        wp.enabled = true;
        return wp;
    }
}

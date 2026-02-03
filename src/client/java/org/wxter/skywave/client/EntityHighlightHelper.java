package org.wxter.skywave.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.wxter.skywave.client.HypixelSkyblockContext;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.List;
import java.util.Locale;

/**
 * Resolves entity display names for mob highlighting. Works with entity custom names,
 * scoreboard team display names (Hypixel-style), and armor stands above the mob.
 */
public final class EntityHighlightHelper {

    private EntityHighlightHelper() {}

    /**
     * Returns true if the entity's resolved display name matches any configured nametag.
     */
    public static boolean matchesNametags(Entity entity) {
        if (entity instanceof ArmorStandEntity) return false;
        if (!HypixelSkyblockContext.isOnHypixelSkyblock()) return false;
        SkywaveConfig config = SkywaveConfig.get();
        if (!config.mobHighlightEnabled || config.mobHighlightNametags == null || config.mobHighlightNametags.isEmpty()) {
            return false;
        }
        String displayName = resolveDisplayName(entity);
        if (displayName == null || displayName.isEmpty()) return false;
        String trimmed = displayName.trim();
        String trimmedLower = trimmed.toLowerCase(Locale.ROOT);
        for (String tag : config.mobHighlightNametags) {
            if (tag == null) continue;
            String t = tag.trim();
            if (t.isEmpty()) continue;
            String tLower = t.toLowerCase(Locale.ROOT);
            if (trimmedLower.equals(tLower) || trimmedLower.contains(tLower)) return true;
        }
        return false;
    }

    /**
     * Resolves the visible nametag for an entity (custom name, team display name,
     * armor stand above, or default name).
     */
    public static String resolveDisplayName(Entity entity) {
        if (entity.hasCustomName()) {
            Text custom = entity.getCustomName();
            if (custom != null) {
                String s = custom.getString();
                if (s != null && !s.isEmpty()) return s;
            }
        }

        World world = entity.getEntityWorld();
        if (world != null) {
            Box box = entity.getBoundingBox();
            Box search = new Box(
                    box.minX - 0.5, box.minY, box.minZ - 0.5,
                    box.maxX + 0.5, box.maxY + 3.0, box.maxZ + 0.5
            );

            List<ArmorStandEntity> stands = world.getEntitiesByClass(ArmorStandEntity.class, search, e -> true);
            ArmorStandEntity best = null;
            double bestScore = Double.MAX_VALUE;
            // center of entity for horizontal proximity checks
            Vec3d center = box.getCenter();
            double minAllowedY = box.minY + (box.getLengthY() * 0.6);

            for (ArmorStandEntity stand : stands) {
                if (!stand.hasCustomName()) continue;

                // require the stand to be near the top of the entity (player-model NPC stands can sit slightly below maxY)
                if (stand.getY() < minAllowedY) continue;

                // horizontal distance between stand and entity center
                double dx = stand.getX() - center.x;
                double dz = stand.getZ() - center.z;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                if (horiz > 1.5) continue; // too far horizontally

                // prefer stands that are very close and above; score by horiz + vertical offset
                double score = horiz + Math.abs(stand.getY() - box.maxY);
                if (score < bestScore) {
                    bestScore = score;
                    best = stand;
                }
            }

            if (best != null) {
                Text name = best.getCustomName();
                if (name != null) {
                    String s = name.getString();
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        }

        Team team = entity.getScoreboardTeam();
        if (team != null) {
            Text displayName = team.getDisplayName();
            if (displayName != null) {
                String s = displayName.getString();
                if (s != null && !s.isEmpty()) return s;
            }
        }

        Text displayName = entity.getDisplayName();
        if (displayName != null) {
            String s = displayName.getString();
            if (s != null && !s.isEmpty()) return s;
        }

        return entity.getName().getString();
    }
}

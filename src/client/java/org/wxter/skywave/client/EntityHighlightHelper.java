package org.wxter.skywave.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.List;

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
        SkywaveConfig config = SkywaveConfig.get();
        if (!config.mobHighlightEnabled || config.mobHighlightNametags == null || config.mobHighlightNametags.isEmpty()) {
            return false;
        }
        String displayName = resolveDisplayName(entity);
        if (displayName == null || displayName.isEmpty()) return false;
        String trimmed = displayName.trim();
        for (String tag : config.mobHighlightNametags) {
            if (tag == null) continue;
            String t = tag.trim();
            if (t.isEmpty()) continue;
            if (trimmed.equalsIgnoreCase(t) || trimmed.contains(t)) return true;
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

        Team team = entity.getScoreboardTeam();
        if (team != null) {
            Text displayName = team.getDisplayName();
            if (displayName != null) {
                String s = displayName.getString();
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
            for (ArmorStandEntity stand : stands) {
                if (stand.hasCustomName()) {
                    Text name = stand.getCustomName();
                    if (name != null) {
                        String s = name.getString();
                        if (s != null && !s.isEmpty()) return s;
                    }
                }
            }
        }

        return entity.getName().getString();
    }
}

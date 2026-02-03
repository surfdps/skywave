package org.wxter.skywave.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.scoreboard.Scoreboard;

import java.lang.reflect.Method;
import java.util.Locale;

public final class HypixelSkyblockContext {
    private HypixelSkyblockContext() {}

    public static boolean isOnHypixelSkyblock() {
        // Temporarily disabled: allow features outside SkyBlock until server/game detection is finalized.
        // (User request: "comment out and disable for now that the functions only work in Skyblock".)
        return true;
    }

    public static boolean isOnHypixelSkyblock(MinecraftClient client) {
        // Temporarily disabled: allow features outside SkyBlock until server/game detection is finalized.
        return true;
        /*
        if (client == null || client.world == null || client.player == null) return false;
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.address == null) return false;
        String address = server.address.toLowerCase(Locale.ROOT);
        if (!address.contains("hypixel.net")) return false;

        String sidebarTitle = getSidebarTitleSafe(client);
        if (sidebarTitle == null || sidebarTitle.isBlank()) return false;
        return sidebarTitle.toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        */
    }

    private static String getSidebarTitleSafe(MinecraftClient client) {
        try {
            Scoreboard scoreboard = client.world.getScoreboard();
            Object objective = getSidebarObjective(scoreboard);
            if (objective == null) return null;

            // Try getDisplayName(): Text
            try {
                Method m = objective.getClass().getMethod("getDisplayName");
                Object text = m.invoke(objective);
                String s = getTextString(text);
                if (s != null && !s.isBlank()) return s;
            } catch (Throwable ignored) {}

            // Fallback getName(): String
            try {
                Method m = objective.getClass().getMethod("getName");
                Object name = m.invoke(objective);
                return name == null ? null : String.valueOf(name);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object getSidebarObjective(Scoreboard scoreboard) {
        if (scoreboard == null) return null;
        // Newer versions: getObjectiveForSlot(ScoreboardDisplaySlot)
        try {
            Class<?> slotClass = Class.forName("net.minecraft.scoreboard.ScoreboardDisplaySlot");
            Object sidebar = Enum.valueOf((Class<? extends Enum>) slotClass.asSubclass(Enum.class), "SIDEBAR");
            Method m = scoreboard.getClass().getMethod("getObjectiveForSlot", slotClass);
            return m.invoke(scoreboard, sidebar);
        } catch (Throwable ignored) {}

        // Older versions: getObjectiveForSlot(int)
        try {
            Method m = scoreboard.getClass().getMethod("getObjectiveForSlot", int.class);
            return m.invoke(scoreboard, 1);
        } catch (Throwable ignored) {}

        return null;
    }

    private static String getTextString(Object text) {
        if (text == null) return null;
        try {
            Method m = text.getClass().getMethod("getString");
            Object s = m.invoke(text);
            return s == null ? null : String.valueOf(s);
        } catch (Throwable ignored) {}
        return String.valueOf(text);
    }
}

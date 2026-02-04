package org.wxter.skywave.client.waypoints;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.wxter.skywave.config.SkywaveConfig;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CustomWaypointsRenderer {
    private CustomWaypointsRenderer() {}

    public static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        MatrixStack matrices = context.matrices();
        if (matrices == null) return;
        net.minecraft.client.render.VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        SkywaveConfig config = SkywaveConfig.get();
        SkywaveConfig.WaypointsConfig cfg = config.waypoints;
        if (cfg == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        String dimensionId = mc.world.getRegistryKey().getValue().toString();
        int light = 0xF000F0; // full bright, easier to read

        boolean anyRendered = false;

        if (cfg.enabled) {
            anyRendered |= renderWaypoints(CustomWaypoints.activeWaypoints(), cfg, dimensionId, matrices, camera, camPos, consumers, light, mc.textRenderer);
        }

        if (config.crystalNucleus != null && config.crystalNucleus.jungleSkipWaypointsEnabled) {
            anyRendered |= renderWaypoints(JungleSkipWaypoints.getWaypoints(), cfg, dimensionId, matrices, camera, camPos, consumers, light, mc.textRenderer);
        }

        if (!anyRendered) return;
    }

    private static boolean renderWaypoints(
            List<SkywaveConfig.WaypointEntry> waypoints,
            SkywaveConfig.WaypointsConfig cfg,
            String currentDimensionId,
            MatrixStack matrices,
            Camera camera,
            Vec3d camPos,
            net.minecraft.client.render.VertexConsumerProvider consumers,
            int light,
            TextRenderer textRenderer
    ) {
        if (waypoints == null || waypoints.isEmpty()) return false;

        boolean renderedAny = false;
        for (SkywaveConfig.WaypointEntry wp : new ArrayList<>(waypoints)) {
            if (wp == null || !wp.enabled) continue;
            if (cfg.onlySameDimension && wp.dimension != null && !wp.dimension.isBlank() && !currentDimensionId.equals(wp.dimension)) {
                continue;
            }

            Vec3d waypointPos = new Vec3d(wp.x + 0.5, wp.y + 1.0, wp.z + 0.5);
            Vec3d relativePos = waypointPos.subtract(camPos);
            double distance = relativePos.length();

            renderLabel(matrices, consumers, camera, relativePos, wp, cfg.showDistance, distance, light, textRenderer);
            renderedAny = true;

            if (cfg.highlightBlockInFov && isInFov(camera, camPos, waypointPos, MinecraftClient.getInstance().options.getFov().getValue())) {
                renderBlockHighlight(matrices, consumers, camPos, wp);
            }
        }

        return renderedAny;
    }

    private static void renderLabel(
            MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider consumers,
            Camera camera,
            Vec3d relativePos,
            SkywaveConfig.WaypointEntry wp,
            boolean showDistance,
            double distance,
            int light
            , TextRenderer textRenderer
    ) {
        int rgb = wp.color & 0xFFFFFF;
        int nameColor = 0xFF000000 | rgb;
        int nameOutline = outlineColorFor(rgb);
        int bgColor = 0x00000000;

        String name = wp.name == null || wp.name.isBlank() ? "Waypoint" : wp.name;
        Text nameText = Text.literal(name).formatted(Formatting.BOLD);
        var nameOrdered = nameText.asOrderedText();

        matrices.push();
        matrices.translate(relativePos.x, relativePos.y, relativePos.z);
        matrices.multiply(camera.getRotation());

        // Scale in world-space so the label stays readable at any distance, with a larger cap when close.
        // "Min size" = the old constant screen size; "Max size" = 2x that size.
        float baseScaleAt20m = 0.03f;
        float baseScreenScale = baseScaleAt20m / 20.0f;
        float minScreenScale = baseScreenScale;
        float maxScreenScale = baseScreenScale * 2.0f;

        float dist = (float) Math.max(0.5, distance);
        float t = MathHelper.clamp((dist - 8.0f) / (32.0f - 8.0f), 0.0f, 1.0f);
        float screenScale = MathHelper.lerp(t, maxScreenScale, minScreenScale);
        float scale = screenScale * dist;
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float nameX = -textRenderer.getWidth(nameOrdered) / 2f;
        float nameY = 0f;

        GlStateManager._disableDepthTest();
        try {
            drawOutlinedText(textRenderer, consumers, matrix, nameOrdered, nameX, nameY, nameColor, nameOutline, bgColor, light);

            if (showDistance) {
                Text distText = Text.literal(formatDistance(distance));
                var distOrdered = distText.asOrderedText();
                float distX = -textRenderer.getWidth(distOrdered) / 2f;
                float distY = textRenderer.fontHeight + 5f;
                int distColor = 0xFFFFFFFF;
                int distOutline = 0xFF202020;
                drawOutlinedText(textRenderer, consumers, matrix, distOrdered, distX, distY, distColor, distOutline, bgColor, light);
            }
        } finally {
            GlStateManager._enableDepthTest();
        }

        matrices.pop();
    }

    private static void renderBlockHighlight(
            MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider consumers,
            Vec3d camPos,
            SkywaveConfig.WaypointEntry wp
    ) {
        BlockPos blockPos = new BlockPos(wp.x, wp.y, wp.z);
        int argb = wp.color;
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;
        float alpha = 0.35f;

        matrices.push();
        matrices.translate(blockPos.getX() - camPos.x, blockPos.getY() - camPos.y, blockPos.getZ() - camPos.z);

        DebugRenderer.drawBox(matrices, consumers, 0, 0, 0, 1, 1, 1, red, green, blue, alpha);

        matrices.pop();
    }

    private static int outlineColorFor(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float luminance = (0.2126f * r) + (0.7152f * g) + (0.0722f * b);
        return luminance < 0.35f ? 0xFFECECEC : 0xFF202020;
    }

    private static void drawOutlinedText(
            TextRenderer textRenderer,
            net.minecraft.client.render.VertexConsumerProvider consumers,
            Matrix4f matrix,
            net.minecraft.text.OrderedText text,
            float x,
            float y,
            int color,
            int outlineColor,
            int backgroundColor,
            int light
    ) {
        TextRenderer.TextLayerType layer = TextRenderer.TextLayerType.SEE_THROUGH;

        float o = 0.5f; // thinner outline
        textRenderer.draw(text, x - o, y, outlineColor, false, matrix, consumers, layer, 0, light);
        textRenderer.draw(text, x + o, y, outlineColor, false, matrix, consumers, layer, 0, light);
        textRenderer.draw(text, x, y - o, outlineColor, false, matrix, consumers, layer, 0, light);
        textRenderer.draw(text, x, y + o, outlineColor, false, matrix, consumers, layer, 0, light);

        textRenderer.draw(text, x, y, color, false, matrix, consumers, layer, backgroundColor, light);
    }

    private static boolean isInFov(Camera camera, Vec3d camPos, Vec3d targetPos, double fovDegrees) {
        Vec3d lookVec = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        Vec3d toTarget = targetPos.subtract(camPos).normalize();
        double dot = MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0);
        double angle = Math.acos(dot) * (180.0 / Math.PI);
        return angle <= fovDegrees / 2.0;
    }

    private static String formatDistance(double blocks) {
        if (blocks < 1_000.0) {
            return String.format(Locale.ROOT, "%.0fm", blocks);
        }
        return String.format(Locale.ROOT, "%.1fkm", blocks / 1_000.0);
    }
}

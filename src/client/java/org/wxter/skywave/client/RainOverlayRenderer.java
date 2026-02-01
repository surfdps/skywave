package org.wxter.skywave.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.wxter.skywave.config.SkywaveConfig;

public final class RainOverlayRenderer {

    private static volatile Text message = null;
    private static volatile int remainingTicks = 0;

    private static final int DEFAULT_TICKS = 240;

    // public для регистрации в HudRenderCallback
    public static void render(DrawContext ctx, RenderTickCounter tickDelta) {
        if (message == null || remainingTicks <= 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int x = resolveHudX(screenW);
        int y = SkywaveConfig.get().rainReminderHudY;
        int color = 0xFF00BFFF;

        ctx.getMatrices().pushMatrix();
        float scale = 2.0f; // увеличение в 2 раза
        ctx.getMatrices().scale(scale, scale);

        // деление координат на scale при масштабировании
        int scaledX = x / (int) scale;
        int scaledY = y / (int) scale;

        ctx.drawCenteredTextWithShadow(mc.textRenderer, message, scaledX, scaledY, color);

        ctx.getMatrices().popMatrix();

        remainingTicks--;
        if (remainingTicks <= 0) message = null;
    }

    public static void show(Text txt, int ticks) {
        if (txt == null) return;
        message = txt;
        remainingTicks = ticks > 0 ? ticks : DEFAULT_TICKS;
    }

    public static void renderMovePreview(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int x = resolveHudX(screenW);
        int y = SkywaveConfig.get().rainReminderHudY;
        Text preview = Text.literal("Rain Reminder");

        ctx.getMatrices().pushMatrix();
        float scale = 2.0f;
        ctx.getMatrices().scale(scale, scale);
        int scaledX = x / (int) scale;
        int scaledY = y / (int) scale;
        ctx.drawCenteredTextWithShadow(mc.textRenderer, preview, scaledX, scaledY, 0xFF00BFFF);
        ctx.getMatrices().popMatrix();
    }

    public static int resolveHudX(int screenW) {
        int x = SkywaveConfig.get().rainReminderHudX;
        if (x < 0) {
            x = screenW / 2;
        }
        return x;
    }

    public static int getHudY() {
        return SkywaveConfig.get().rainReminderHudY;
    }

    public static void setHudPosition(int x, int y) {
        SkywaveConfig.get().rainReminderHudX = x;
        SkywaveConfig.get().rainReminderHudY = y;
        SkywaveConfig.save();
    }

    public static int getPreviewWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return 0;
        return mc.textRenderer.getWidth("Rain Reminder") * 2;
    }

    public static int getPreviewHeight() {
        return MinecraftClient.getInstance() == null ? 0 : mcLineHeight() * 2;
    }

    private static int mcLineHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? 9 : mc.textRenderer.fontHeight;
    }
}

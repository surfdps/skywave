package org.wxter.skywave.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class RainOverlayRenderer {

    private static volatile Text message = null;
    private static volatile int remainingTicks = 0;

    private static final int TOP_OFFSET = 80;
    private static final int DEFAULT_TICKS = 240;

    // Сделали public для регистрации в HudRenderCallback
    public static void render(DrawContext ctx, RenderTickCounter tickDelta) {
        if (message == null || remainingTicks <= 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        int y = TOP_OFFSET;
        int color = 0xFF00BFFF;

        ctx.getMatrices().pushMatrix();
        float scale = 2.0f; // увеличение в 2 раза
        ctx.getMatrices().scale(scale, scale);

        // при масштабировании координаты нужно делить на scale
        int scaledX = screenW / 2 / (int)scale;
        int scaledY = y / (int)scale;

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
}
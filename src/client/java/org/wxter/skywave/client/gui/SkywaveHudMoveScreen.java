package org.wxter.skywave.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;
import net.minecraft.util.Formatting;

public class SkywaveHudMoveScreen extends Screen {

    private final Screen parent;
    private DragTarget draggingTarget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public SkywaveHudMoveScreen(Screen parent) {
        super(Text.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        // Кнопка закрытия
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(centerX - 100, centerY + 50, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // затемнение фона
        this.renderDarkening(ctx);

        // Инструкция
        int centerX = width / 2;
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Drag HUD elements to move them").formatted(Formatting.AQUA),
                centerX,
                20,
                0xFFFFFF
        );

        // Рендер HUD поверх фона
        HuntingProfitTracker.INSTANCE.renderMovePreview(ctx);
        org.wxter.skywave.client.RainOverlayRenderer.renderMovePreview(ctx);

        // Кнопки поверх
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0) {
            HuntingProfitTracker.HudBounds huntingBounds = HuntingProfitTracker.INSTANCE.getHudBounds();
            if (huntingBounds.contains((int) mouseX, (int) mouseY)) {
                draggingTarget = DragTarget.HUNTING;
                dragOffsetX = (int) mouseX - huntingBounds.x();
                dragOffsetY = (int) mouseY - huntingBounds.y();
                return true;
            }

            int screenW = this.client != null ? this.client.getWindow().getScaledWidth() : 0;
            int rainCenterX = org.wxter.skywave.client.RainOverlayRenderer.resolveHudX(screenW);
            int rainY = org.wxter.skywave.client.RainOverlayRenderer.getHudY();
            int rainWidth = org.wxter.skywave.client.RainOverlayRenderer.getPreviewWidth();
            int rainHeight = org.wxter.skywave.client.RainOverlayRenderer.getPreviewHeight();
            int rainX = rainCenterX - (rainWidth / 2);
            if (mouseX >= rainX && mouseX <= rainX + rainWidth && mouseY >= rainY && mouseY <= rainY + rainHeight) {
                draggingTarget = DragTarget.RAIN_REMINDER;
                dragOffsetX = (int) mouseX - rainCenterX;
                dragOffsetY = (int) mouseY - rainY;
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0 && draggingTarget != null) {
            if (draggingTarget == DragTarget.HUNTING) {
                HuntingProfitTracker.INSTANCE.setHudPosition((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY);
                return true;
            }
            if (draggingTarget == DragTarget.RAIN_REMINDER) {
                org.wxter.skywave.client.RainOverlayRenderer.setHudPosition((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY);
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingTarget = null;
        }
        return super.mouseReleased(click);
    }

    private enum DragTarget {
        HUNTING,
        RAIN_REMINDER
    }
}

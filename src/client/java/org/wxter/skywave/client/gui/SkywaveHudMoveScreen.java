package org.wxter.skywave.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;
import org.wxter.skywave.config.SkywaveConfig;
import net.minecraft.util.Formatting;

public class SkywaveHudMoveScreen extends Screen {

    private final Screen parent;

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
            HuntingProfitTracker.INSTANCE.disableMoveMode();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(centerX - 100, centerY + 50, 200, 20).build());

        // Вкл/выкл таймер
        addDrawableChild(ButtonWidget.builder(Text.literal("Toggle Timer Pause"), b -> {
            HuntingProfitTracker.INSTANCE.toggleTimerPause();
        }).dimensions(centerX - 100, centerY + 20, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // затемнение фона
        this.renderDarkening(ctx);

        // Инструкция
        int centerX = width / 2;
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Drag the HUD box to move it").formatted(Formatting.AQUA),
                centerX,
                20,
                0xFFFFFF
        );

        // Рендер HUD поверх фона
        HuntingProfitTracker.INSTANCE.onHudRender(ctx);

        // Кнопки поверх
        super.render(ctx, mouseX, mouseY, delta);
    }
}
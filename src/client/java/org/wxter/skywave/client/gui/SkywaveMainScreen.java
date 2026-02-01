package org.wxter.skywave.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SkywaveMainScreen extends Screen {

    private final Screen parent;

    public SkywaveMainScreen(Screen parent) {
        super(Text.literal("Skywave Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Config"), b ->
                client.setScreen(SkywaveYaclGui.create(this))
        ).dimensions(centerX - 100, centerY - 10, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b ->
                client.setScreen(parent)
        ).dimensions(centerX - 100, centerY + 15, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {

//        this.renderDarkening(ctx);
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int centerX = this.width / 2;

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Skywave").formatted(Formatting.AQUA, Formatting.BOLD),
                centerX,
                this.height / 2 - 50,
                0xFFFFFFFF // белый с полной альфой
        );

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("v0.2  •  by wxxve").formatted(Formatting.GRAY),
                centerX,
                this.height / 2 - 35,
                0xFFB8B8B8 // светло-серый с полной альфой
        );

        // Теперь рендерим кнопки/виджеты поверх фона
        super.render(ctx, mouseX, mouseY, delta);
    }
}
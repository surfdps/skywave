package org.wxter.skywave.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

public class SkywaveMainScreen extends Screen {

    private final Screen parent;
    private int buttonsTop = 0;
    private int buttonsTotalHeight = 0;

    public SkywaveMainScreen(Screen parent) {
        super(Text.literal("Skywave Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 5;

        List<ButtonWidget> buttons = List.of(
                ButtonWidget.builder(Text.literal("Config"), b ->
                        client.setScreen(SkywaveYaclGui.create(this))
                ).dimensions(0, 0, buttonWidth, buttonHeight).build(),
                ButtonWidget.builder(Text.literal("Move GUI"), b ->
                        client.setScreen(new SkywaveHudMoveScreen(MinecraftClient.getInstance().currentScreen))
                ).dimensions(0, 0, buttonWidth, buttonHeight).build(),
                ButtonWidget.builder(Text.literal("Close"), b ->
                        client.setScreen(parent)
                ).dimensions(0, 0, buttonWidth, buttonHeight).build()
        );

        buttonsTotalHeight = buttons.size() * (buttonHeight + spacing) - spacing;
        buttonsTop = height / 2 - buttonsTotalHeight / 2 + 12;
        int y = buttonsTop;
        for (ButtonWidget button : buttons) {
            button.setX(centerX - buttonWidth / 2);
            button.setY(y);
            addDrawableChild(button);
            y += buttonHeight + spacing;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int centerX = this.width / 2;

        int titleY = Math.max(20, buttonsTop - 40);
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Skywave").formatted(Formatting.AQUA, Formatting.BOLD),
                centerX,
                titleY,
                0xFFFFFFFF
        );

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("v1.2  •  by wxxve & eseoo").formatted(Formatting.GRAY),
                centerX,
                titleY + 15,
                0xFFB8B8B8
        );

        super.render(ctx, mouseX, mouseY, delta);
    }
}

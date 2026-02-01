package org.wxter.skywave.client.gui;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.wxter.skywave.config.SkywaveConfig;

import java.awt.Color;

public class SkywaveYaclGui {

    private SkywaveYaclGui() {}

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Skywave Settings"))
                // Category
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Fishing"))
                        // ----- Rain Reminder group (header + options) -----
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Rain Reminder"))
                                // enabled
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Enable Rain Reminder"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Notify when rain stops so you can quickly return to Vanessa."
                                        )))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().rainReminderEnabled,
                                                v -> SkywaveConfig.get().rainReminderEnabled = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                // delivery type (enum dropdown/cycler)
                                .option(Option.<SkywaveConfig.RainReminderType>createBuilder()
                                        .name(Text.literal("Reminder delivery"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Choose where the notification appears: chat or on-screen message.\n\nRequires enabled Rain Reminder feature!"
                                        )))
                                        .binding(
                                                SkywaveConfig.get().rainReminderType,               // default (не критично)
                                                () -> SkywaveConfig.get().rainReminderType,        // getter
                                                (SkywaveConfig.RainReminderType t) -> SkywaveConfig.get().rainReminderType = t // setter
                                        )
                                        // правильно: принимаем option и на builder указываем enumClass(...)
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(SkywaveConfig.RainReminderType.class))
                                        .build())

                                // sound toggle
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Reminder sound"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Play a short tone when rain ends.\n\nRequires enabled Rain Reminder feature!"
                                        )))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().rainReminderSound,
                                                v -> SkywaveConfig.get().rainReminderSound = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .build()) // end Rain Reminder group

                        // ----- Mob Highlight group (separated visually) -----
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Mobs Highlight"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Night Squid Highlight"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Highlights Night Squids with a glow so you can see them easier while fishing."
                                        )))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().nightSquidHighlight,
                                                v -> SkywaveConfig.get().nightSquidHighlight = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<java.awt.Color>createBuilder()
                                        .name(Text.literal("Highlight color"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Pick the color used for the Night Squid outline."
                                        )))
                                        .binding(
                                                new Color(SkywaveConfig.get().nightSquidColor, true),
                                                () -> new Color(SkywaveConfig.get().nightSquidColor, true),
                                                (Color c) -> SkywaveConfig.get().nightSquidColor = c.getRGB()
                                        )
                                        .controller(ColorControllerBuilder::create)
                                        .build())

                                .build()) // end Mob Highlight group

                        .build()) // end category
                .save(() -> SkywaveConfig.save())
                .build()
                .generateScreen(parent);
    }

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new SkywaveMainScreen(null));
    }
}
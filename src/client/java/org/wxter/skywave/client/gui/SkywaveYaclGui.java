package org.wxter.skywave.client.gui;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.wxter.skywave.config.SkywaveConfig;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;

import java.awt.Color;
import java.util.List;

public class SkywaveYaclGui {

    private SkywaveYaclGui() {}

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Skywave Settings"))

                // Fishing Category
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
                        .build()) // end category

                // QOL Category
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Quality of Life"))
                    // ----- Mob Highlight group (separated visually) -----
                    .group(OptionGroup.createBuilder()
                            .name(Text.literal("Mobs Highlight"))
                            .option(Option.<Boolean>createBuilder()
                                    .name(Text.literal("Enable Mobs Highlight"))
                                    .description(OptionDescription.of(Text.literal(
                                            "Highlight mobs by nametag with a glow. Works with custom names, scoreboard team names, and Hypixel-style armor stand name tags."
                                    )))
                                    .binding(
                                            true,
                                            () -> SkywaveConfig.get().mobHighlightEnabled,
                                            v -> SkywaveConfig.get().mobHighlightEnabled = v
                                    )
                                    .controller(TickBoxControllerBuilder::create)
                                    .build())

                            .option(Option.<java.awt.Color>createBuilder()
                                    .name(Text.literal("Highlight color"))
                                    .description(OptionDescription.of(Text.literal(
                                            "Color used for the mob outline glow."
                                    )))
                                    .binding(
                                            new Color(SkywaveConfig.get().mobHighlightColor, true),
                                            () -> new Color(SkywaveConfig.get().mobHighlightColor, true),
                                            (Color c) -> SkywaveConfig.get().mobHighlightColor = c.getRGB()
                                    )
                                    .controller(ColorControllerBuilder::create)
                                    .build())

                            .build()) // end Mob Highlight group

                    // ListOption must be added directly to category (not inside a group)
                    .option(ListOption.<String>createBuilder()
                            .name(Text.literal("Nametags to Highlight"))
                            .description(OptionDescription.of(Text.literal(
                                    "Add nametags to highlight (e.g. Night Squid, Golden Goblin). Matches the visible name from any source (entity, team, or armor stand above mob). Doesn`t work through walls."
                            )))
                            .binding(
                                    new ArrayList<>(List.of("Night Squid")),
                                    () -> new ArrayList<>(SkywaveConfig.get().mobHighlightNametags),
                                    list -> SkywaveConfig.get().mobHighlightNametags = new ArrayList<>(list)
                            )
                            .controller(StringControllerBuilder::create)
                            .initial("")
                            .build())
                        .build())

                // Hunting Category
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Hunting"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Profit Tracker"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Hunting Profit Tracker"))
                                        .description(OptionDescription.of(Text.literal("Track profit from shards via hunting.\n\nOnly tracks shards, no other items!")))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hunting.profitTrackerEnabled,
                                                v -> SkywaveConfig.get().hunting.profitTrackerEnabled = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Start Counting (Start/Stop)"))
                                        .description(OptionDescription.of(Text.literal("Toggle shards counting.\n\nAlso clickable on HUD)")))
                                        .binding(
                                                false,
                                                HuntingProfitTracker.INSTANCE::isRunning,
                                                (Boolean v) -> {
                                                    if (v) HuntingProfitTracker.INSTANCE.startSession();
                                                    else HuntingProfitTracker.INSTANCE.stopSession();
                                                }
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<SkywaveConfig.DisplayMode>createBuilder()
                                        .name(Text.literal("Display Mode"))
                                        .description(OptionDescription.of(Text.literal("Choose whether HUD shows Total or Session shards profit.")))
                                        .binding(
                                                SkywaveConfig.get().hunting.displayMode,
                                                () -> SkywaveConfig.get().hunting.displayMode,
                                                (SkywaveConfig.DisplayMode m) -> SkywaveConfig.get().hunting.displayMode = m
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(SkywaveConfig.DisplayMode.class))
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Show Timer"))
                                        .description(OptionDescription.of(Text.literal("Show session timer on the tracker HUD.")))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hunting.showTimer,
                                                v -> SkywaveConfig.get().hunting.showTimer = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .build())
                        .build())

                .save(SkywaveConfig::save)
                .build()
                .generateScreen(parent);
    }

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new SkywaveMainScreen(null));
    }
}
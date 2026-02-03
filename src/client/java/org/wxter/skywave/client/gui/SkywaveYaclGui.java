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
                                        .name(Text.literal("Reminder Type"))
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
                                        .name(Text.literal("Reminder Sound"))
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

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("HUD"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("HUD background panels"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Draw a subtle background panel behind trackers (applies to all trackers)."
                                        )))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hudBackgroundPanelsEnabled,
                                                v -> SkywaveConfig.get().hudBackgroundPanelsEnabled = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
                        .build())

                // Hunting Category
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Hunting"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Profit Tracker"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Hunting Profit Tracker"))
                                        .description(OptionDescription.of(Text.literal("Track profit from shards when hunting.\n\nOnly tracks shards, no other items!")))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hunting.profitTrackerEnabled,
                                                v -> SkywaveConfig.get().hunting.profitTrackerEnabled = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<SkywaveConfig.DisplayMode>createBuilder()
                                        .name(Text.literal("Display Mode"))
                                        .description(OptionDescription.of(Text.literal("Choose whether HUD shows Total or Session shards profit.\n\nAlso clickable in UI.")))
                                        .binding(
                                                SkywaveConfig.get().hunting.displayMode,
                                                () -> SkywaveConfig.get().hunting.displayMode,
                                                (SkywaveConfig.DisplayMode m) -> SkywaveConfig.get().hunting.displayMode = m
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(SkywaveConfig.DisplayMode.class))
                                        .build())

                                .option(Option.<SkywaveConfig.BazaarPriceMode>createBuilder()
                                        .name(Text.literal("Price Mode"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Choose which bazaar value to use for shard profit: buy offer or sell offer.\n\nAlso clickable in the tracker UI."
                                        )))
                                        .binding(
                                                SkywaveConfig.get().hunting.bazaarPriceMode,
                                                () -> SkywaveConfig.get().hunting.bazaarPriceMode,
                                                (SkywaveConfig.BazaarPriceMode m) -> SkywaveConfig.get().hunting.bazaarPriceMode = m
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(SkywaveConfig.BazaarPriceMode.class))
                                        .build())

                                .option(Option.<SkywaveConfig.HuntingSortMode>createBuilder()
                                        .name(Text.literal("Items Sorting"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Choose how items are sorted in the tracker list.\n\nProfit: highest profit first.\nRarity: highest rarity first."
                                        )))
                                        .binding(
                                                SkywaveConfig.HuntingSortMode.PROFIT,
                                                () -> SkywaveConfig.get().hunting.sortMode,
                                                v -> SkywaveConfig.get().hunting.sortMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(SkywaveConfig.HuntingSortMode.class))
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Show Timer"))
                                        .description(OptionDescription.of(Text.literal("Show session timer in tracker. Starting/Pausing manually in UI.")))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hunting.showTimer,
                                                v -> SkywaveConfig.get().hunting.showTimer = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Show Lootshare shards"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Show Lootshare shards as separate lines (tagged \"LS\").\n\nIf disabled, Lootshare shards are merged into the main shard totals."
                                        )))
                                        .binding(
                                                true,
                                                () -> SkywaveConfig.get().hunting.showLootshareShards,
                                                v -> SkywaveConfig.get().hunting.showLootshareShards = v
                                        )
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Count “Sent to Hunting Box”"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Count shards from messages like \"You sent ... to your Hunting Box\".\n\n" +
                                                "This lets you track shards you manually add to the Hunting Box, but it may cause Coins/h to be calculated incorrectly."
                                        )))
                                        .binding(
                                                false,
                                                () -> SkywaveConfig.get().hunting.countSentToHuntingBox,
                                                v -> SkywaveConfig.get().hunting.countSentToHuntingBox = v
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

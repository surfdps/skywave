// src/main/java/org/wxter/skywave/client/SkywaveClient.java
package org.wxter.skywave.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;
import org.wxter.skywave.client.gui.CustomWaypointsScreen;
import org.wxter.skywave.client.gui.SkywaveHudMoveScreen;
import org.wxter.skywave.client.gui.SkywaveMainScreen;
import org.wxter.skywave.client.RainReminderHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.wxter.skywave.config.SkywaveConfig;
import org.wxter.skywave.ModConstants;
import org.wxter.skywave.client.RainOverlayRenderer;
import org.wxter.skywave.client.waypoints.CustomWaypointsRenderer;
import org.wxter.skywave.client.waypoints.JungleSkipWaypoints;
import org.wxter.skywave.client.waypoints.WaypointChatParser;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class SkywaveClient implements ClientModInitializer {

    private static volatile boolean openGuiNextTick = false;
    private static volatile boolean openHudMoveNextTick = false;
    private static volatile boolean openWaypointsNextTick = false;

    @Override
    public void onInitializeClient() {
        SkywaveConfig.load();
        ModConstants.LOGGER.info("Skywave Client Initialized");

        // Rain overlay remains (your existing)
        HudRenderCallback.EVENT.register(RainOverlayRenderer::render);

        // init tracker (внутри он зарегистрирует слушатель и HUD)
        // Custom Waypoints (world rendering)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(CustomWaypointsRenderer::render);

        HuntingProfitTracker.INSTANCE.init();
        WaypointChatParser.init();
        NightSquidAlertHandler.init();

        // tick handler for queued GUI open
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            JungleSkipWaypoints.tick(client);
            if (openGuiNextTick) {
                openGuiNextTick = false;
                if (client != null && client.player != null) {
                    client.setScreen(new SkywaveMainScreen(client.currentScreen));
                } else {
                    ModConstants.LOGGER.warn("Queued GUI open requested but client/player was null");
                }
            }
            if (openHudMoveNextTick) {
                openHudMoveNextTick = false;
                if (client != null && client.player != null) {
                    client.setScreen(new SkywaveHudMoveScreen(client.currentScreen));
                } else {
                    ModConstants.LOGGER.warn("Queued HUD move GUI open requested but client/player was null");
                }
            }
            if (client != null) {
                RainReminderHandler.tick(client);
            }
            if (openWaypointsNextTick) {
                openWaypointsNextTick = false;
                if (client != null && client.player != null) {
                    client.setScreen(new CustomWaypointsScreen(client.currentScreen));
                } else {
                    ModConstants.LOGGER.warn("Queued Waypoints GUI open requested but client/player was null");
                }
            }
            // other tick tasks if needed
        });

        // commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildRootCommand("skywave"));
            dispatcher.register(buildRootCommand("sw"));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRootCommand(String root) {
        return literal(root)
                .executes(ctx -> {
                    openGuiNextTick = true;
                    return 1;
                })
                .then(literal("help")
                        .executes(ctx -> handleHelp(1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> handleHelp(IntegerArgumentType.getInteger(ctx, "page")))))
                .then(literal("menu").executes(ctx -> {
                    openGuiNextTick = true;
                    return 1;
                }))
                // legacy alias (kept for compatibility)
                .then(literal("gui").executes(ctx -> {
                    openHudMoveNextTick = true;
                    return 1;
                }))
                .then(literal("hud").executes(ctx -> {
                    openHudMoveNextTick = true;
                    return 1;
                }))
                .then(literal("hudmove").executes(ctx -> {
                    openHudMoveNextTick = true;
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    SkywaveConfig.load();
                    sendFeedback("Config reloaded.");
                    return 1;
                }))

                // Waypoints
                .then(literal("waypoints")
                        .executes(ctx -> {
                            openWaypointsNextTick = true;
                            return 1;
                        })
                        .then(literal("menu").executes(ctx -> {
                            openWaypointsNextTick = true;
                            return 1;
                        }))
                        .then(literal("on").executes(ctx -> setConfigBool("Custom Waypoints", true, () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Custom Waypoints", false, () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Custom Waypoints", () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))

                        .then(literal("distance")
                                .then(literal("on").executes(ctx -> setConfigBool("Waypoint distance", true, () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Waypoint distance", false, () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Waypoint distance", () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                        )
                        .then(literal("highlight")
                                .then(literal("on").executes(ctx -> setConfigBool("Waypoint highlight", true, () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Waypoint highlight", false, () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Waypoint highlight", () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                        )
                        .then(literal("dimension")
                                .then(literal("on").executes(ctx -> setConfigBool("Only same dimension", true, () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Only same dimension", false, () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Only same dimension", () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                        )
                        .then(literal("chat")
                                .then(literal("yes")
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    boolean ok = WaypointChatParser.accept(id);
                                                    sendFeedback(ok ? "Waypoint created." : "Waypoint request expired.");
                                                    return 1;
                                                })))
                                .then(literal("no")
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    WaypointChatParser.deny(id);
                                                    sendFeedback("Canceled.");
                                                    return 1;
                                                })))
                        )
                )

                // Waypoints alias
                .then(literal("wp")
                        .executes(ctx -> {
                            openWaypointsNextTick = true;
                            return 1;
                        })
                        .then(literal("menu").executes(ctx -> {
                            openWaypointsNextTick = true;
                            return 1;
                        }))
                        .then(literal("on").executes(ctx -> setConfigBool("Custom Waypoints", true, () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Custom Waypoints", false, () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Custom Waypoints", () -> SkywaveConfig.get().waypoints.enabled, v -> SkywaveConfig.get().waypoints.enabled = v)))

                        .then(literal("distance")
                                .then(literal("on").executes(ctx -> setConfigBool("Waypoint distance", true, () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Waypoint distance", false, () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Waypoint distance", () -> SkywaveConfig.get().waypoints.showDistance, v -> SkywaveConfig.get().waypoints.showDistance = v)))
                        )
                        .then(literal("highlight")
                                .then(literal("on").executes(ctx -> setConfigBool("Waypoint highlight", true, () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Waypoint highlight", false, () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Waypoint highlight", () -> SkywaveConfig.get().waypoints.highlightBlockInFov, v -> SkywaveConfig.get().waypoints.highlightBlockInFov = v)))
                        )
                        .then(literal("dimension")
                                .then(literal("on").executes(ctx -> setConfigBool("Only same dimension", true, () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Only same dimension", false, () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Only same dimension", () -> SkywaveConfig.get().waypoints.onlySameDimension, v -> SkywaveConfig.get().waypoints.onlySameDimension = v)))
                        )
                        .then(literal("chat")
                                .then(literal("yes")
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    boolean ok = WaypointChatParser.accept(id);
                                                    sendFeedback(ok ? "Waypoint created." : "Waypoint request expired.");
                                                    return 1;
                                                })))
                                .then(literal("no")
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    WaypointChatParser.deny(id);
                                                    sendFeedback("Canceled.");
                                                    return 1;
                                                })))
                        )
                )

                // Jungle Skip (Crystal Nucleus)
                .then(literal("jungleskip")
                        .executes(ctx -> handleJungleSkipCommand())
                        .then(literal("ready").executes(ctx -> handleJungleSkipReady()))
                        .then(literal("clear").executes(ctx -> {
                            JungleSkipWaypoints.clear();
                            sendFeedback("Jungle Skip waypoints cleared.");
                            return 1;
                        }))
                        .then(literal("on").executes(ctx -> setConfigBool("Jungle Skip Waypoints", true, () -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled, v -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Jungle Skip Waypoints", false, () -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled, v -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Jungle Skip Waypoints", () -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled, v -> SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled = v)))
                )

                // Rain Reminder
                .then(literal("rainreminder")
                        .then(literal("on").executes(ctx -> setConfigBool("Rain Reminder", true, () -> SkywaveConfig.get().rainReminderEnabled, v -> SkywaveConfig.get().rainReminderEnabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Rain Reminder", false, () -> SkywaveConfig.get().rainReminderEnabled, v -> SkywaveConfig.get().rainReminderEnabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Rain Reminder", () -> SkywaveConfig.get().rainReminderEnabled, v -> SkywaveConfig.get().rainReminderEnabled = v)))
                        .then(literal("type")
                                .then(literal("chat").executes(ctx -> {
                                    SkywaveConfig.get().rainReminderType = SkywaveConfig.RainReminderType.CHAT;
                                    SkywaveConfig.save();
                                    sendFeedback("Rain Reminder type: CHAT");
                                    return 1;
                                }))
                                .then(literal("onscreen").executes(ctx -> {
                                    SkywaveConfig.get().rainReminderType = SkywaveConfig.RainReminderType.ONSCREEN;
                                    SkywaveConfig.save();
                                    sendFeedback("Rain Reminder type: ONSCREEN");
                                    return 1;
                                }))
                        )
                        .then(literal("sound")
                                .then(literal("on").executes(ctx -> setConfigBool("Rain Reminder sound", true, () -> SkywaveConfig.get().rainReminderSound, v -> SkywaveConfig.get().rainReminderSound = v)))
                                .then(literal("off").executes(ctx -> setConfigBool("Rain Reminder sound", false, () -> SkywaveConfig.get().rainReminderSound, v -> SkywaveConfig.get().rainReminderSound = v)))
                                .then(literal("toggle").executes(ctx -> toggleConfigBool("Rain Reminder sound", () -> SkywaveConfig.get().rainReminderSound, v -> SkywaveConfig.get().rainReminderSound = v)))
                        )
                )

                // Mob Highlight
                .then(literal("mobhighlight")
                        .then(literal("on").executes(ctx -> setConfigBool("Mob Highlight", true, () -> SkywaveConfig.get().mobHighlightEnabled, v -> SkywaveConfig.get().mobHighlightEnabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Mob Highlight", false, () -> SkywaveConfig.get().mobHighlightEnabled, v -> SkywaveConfig.get().mobHighlightEnabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Mob Highlight", () -> SkywaveConfig.get().mobHighlightEnabled, v -> SkywaveConfig.get().mobHighlightEnabled = v)))
                        .then(literal("list").executes(ctx -> {
                            java.util.List<String> names = SkywaveConfig.get().mobHighlightNametags;
                            if (names == null || names.isEmpty()) {
                                sendFeedback("Mob Highlight list is empty.");
                                return 1;
                            }
                            sendChat(prefix().append(Text.literal("Mob Highlight (" + names.size() + "):").formatted(Formatting.WHITE)));
                            int shown = 0;
                            for (String n : names) {
                                if (n == null || n.isBlank()) continue;
                                sendChat(Text.literal(" - ").formatted(Formatting.DARK_GRAY).append(Text.literal(n).formatted(Formatting.GRAY)));
                                shown++;
                                if (shown >= 10) break;
                            }
                            if (names.size() > shown) {
                                sendChat(Text.literal("... and " + (names.size() - shown) + " more").formatted(Formatting.DARK_GRAY));
                            }
                            return 1;
                        }))
                        .then(literal("add")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (name == null || name.isBlank()) return 0;
                                            if (SkywaveConfig.get().mobHighlightNametags == null) {
                                                SkywaveConfig.get().mobHighlightNametags = new java.util.ArrayList<>();
                                            }
                                            String trimmed = name.trim();
                                            if (!SkywaveConfig.get().mobHighlightNametags.contains(trimmed)) {
                                                SkywaveConfig.get().mobHighlightNametags.add(trimmed);
                                                SkywaveConfig.save();
                                            }
                                            sendFeedback("Added mob highlight: " + trimmed);
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (name == null || name.isBlank()) return 0;
                                            java.util.List<String> list = SkywaveConfig.get().mobHighlightNametags;
                                            if (list == null || list.isEmpty()) {
                                                sendFeedback("Mob Highlight list is empty.");
                                                return 1;
                                            }
                                            boolean removed = list.removeIf(s -> s != null && s.equalsIgnoreCase(name.trim()));
                                            if (removed) SkywaveConfig.save();
                                            sendFeedback(removed ? ("Removed mob highlight: " + name.trim()) : "Mob name not found.");
                                            return 1;
                                        })))
                )

                // Hunting Profit Tracker
                .then(literal("huntingprofittracker")
                        .then(literal("on").executes(ctx -> setConfigBool("Hunting Profit Tracker", true, () -> SkywaveConfig.get().hunting.profitTrackerEnabled, v -> SkywaveConfig.get().hunting.profitTrackerEnabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("Hunting Profit Tracker", false, () -> SkywaveConfig.get().hunting.profitTrackerEnabled, v -> SkywaveConfig.get().hunting.profitTrackerEnabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("Hunting Profit Tracker", () -> SkywaveConfig.get().hunting.profitTrackerEnabled, v -> SkywaveConfig.get().hunting.profitTrackerEnabled = v)))
                        .then(literal("mode")
                                .then(literal("total").executes(ctx -> {
                                    SkywaveConfig.get().hunting.displayMode = SkywaveConfig.DisplayMode.TOTAL;
                                    SkywaveConfig.save();
                                    sendFeedback("Hunting display mode: TOTAL");
                                    return 1;
                                }))
                                .then(literal("session").executes(ctx -> {
                                    SkywaveConfig.get().hunting.displayMode = SkywaveConfig.DisplayMode.SESSION;
                                    SkywaveConfig.save();
                                    sendFeedback("Hunting display mode: SESSION");
                                    return 1;
                                }))
                        )
                )

                // HUD background panels
                .then(literal("hudpanels")
                        .then(literal("on").executes(ctx -> setConfigBool("HUD background panels", true, () -> SkywaveConfig.get().hudBackgroundPanelsEnabled, v -> SkywaveConfig.get().hudBackgroundPanelsEnabled = v)))
                        .then(literal("off").executes(ctx -> setConfigBool("HUD background panels", false, () -> SkywaveConfig.get().hudBackgroundPanelsEnabled, v -> SkywaveConfig.get().hudBackgroundPanelsEnabled = v)))
                        .then(literal("toggle").executes(ctx -> toggleConfigBool("HUD background panels", () -> SkywaveConfig.get().hudBackgroundPanelsEnabled, v -> SkywaveConfig.get().hudBackgroundPanelsEnabled = v)))
                );
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private static int setConfigBool(String name, boolean value, BoolGetter getter, BoolSetter setter) {
        setter.set(value);
        SkywaveConfig.save();
        sendFeedback(name + ": " + (getter.get() ? "Enabled" : "Disabled"));
        return 1;
    }

    private static int toggleConfigBool(String name, BoolGetter getter, BoolSetter setter) {
        boolean next = !getter.get();
        setter.set(next);
        SkywaveConfig.save();
        sendFeedback(name + ": " + (next ? "Enabled" : "Disabled"));
        return 1;
    }

    private static int handleJungleSkipCommand() {
        if (!SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled) {
            sendFeedback("The Jungle Skip Waypoints feature is not enabled. Go to the mod settings and enable it.");
            return 1;
        }

        sendChat(prefix().append(Text.literal("Make sure you are standing inside the right guard in the Jungle Temple, and then press:")));

        Text ready = Text.literal("[Ready]").setStyle(Style.EMPTY
                .withColor(Formatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/sw jungleskip ready"))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to create temporary waypoints")))
        );
        sendChat(ready);
        return 1;
    }

    private static int handleJungleSkipReady() {
        if (!SkywaveConfig.get().crystalNucleus.jungleSkipWaypointsEnabled) {
            sendFeedback("The Jungle Skip Waypoints feature is not enabled. Go to the mod settings and enable it.");
            return 1;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean ok = JungleSkipWaypoints.createFromPlayerPosition(client);
        if (ok) {
            sendFeedback("Jungle Skip waypoints have been set!");
        } else {
            sendFeedback("An error occurred while creating waypoints...");
        }
        return 1;
    }

    private record HelpEntry(String command, String description) {}

    private static final int HELP_PAGE_SIZE = 5;
    private static final java.util.List<HelpEntry> HELP_ENTRIES = java.util.List.of(
            new HelpEntry("/sw help [page]", "Show this list of commands."),
            new HelpEntry("/sw menu", "Open Skywave settings GUI."),
            new HelpEntry("/sw hud", "Open HUD move screen."),
            new HelpEntry("/sw reload", "Reload config from disk."),
            new HelpEntry("/sw waypoints", "Open the waypoint manager."),

            new HelpEntry("/sw waypoints on|off", "Enable/disable Custom Waypoints rendering."),
            new HelpEntry("/sw waypoints distance on|off", "Toggle distance line under waypoint name."),
            new HelpEntry("/sw waypoints highlight on|off", "Toggle block highlight when in FOV."),
            new HelpEntry("/sw wp", "Alias for /sw waypoints."),
            new HelpEntry("/sw rainreminder on|off", "Enable/disable Rain Reminder."),
            new HelpEntry("/sw rainreminder type chat|onscreen", "Set reminder type."),

            new HelpEntry("/sw rainreminder sound on|off", "Toggle Rain Reminder sound."),
            new HelpEntry("/sw mobhighlight on|off", "Enable/disable Mob Highlight."),
            new HelpEntry("/sw mobhighlight add <name>", "Add a mob name to highlight (supports spaces)."),
            new HelpEntry("/sw mobhighlight remove <name>", "Remove a mob name from highlight list."),
            new HelpEntry("/sw jungleskip", "Show Jungle Skip setup prompt."),

            new HelpEntry("/sw jungleskip on|off", "Enable/disable Jungle Skip feature."),
            new HelpEntry("/sw jungleskip clear", "Clear temporary Jungle Skip waypoints."),
            new HelpEntry("/sw huntingprofittracker on|off", "Enable/disable Hunting Profit Tracker."),
            new HelpEntry("/sw huntingprofittracker mode total|session", "Set Hunting display mode."),
            new HelpEntry("/sw hudpanels on|off", "Toggle HUD background panels.")
    );

    private static int handleHelp(int page) {
        int totalPages = Math.max(1, (HELP_ENTRIES.size() + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE);
        int p = Math.max(1, Math.min(totalPages, page));

        sendChat(prefix().append(Text.literal("Commands (Page " + p + "/" + totalPages + ")").formatted(Formatting.WHITE, Formatting.BOLD)));

        int start = (p - 1) * HELP_PAGE_SIZE;
        int end = Math.min(HELP_ENTRIES.size(), start + HELP_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            HelpEntry e = HELP_ENTRIES.get(i);

            Text cmd = Text.literal(e.command).setStyle(Style.EMPTY
                    .withColor(Formatting.YELLOW)
                    .withClickEvent(new ClickEvent.SuggestCommand(e.command))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to paste into chat").formatted(Formatting.GRAY)))
            );

            if (false) {
            MutableText line = Text.literal("• ").formatted(Formatting.DARK_GRAY)
                    .append(cmd)
                    .append(Text.literal(" - " + e.description).formatted(Formatting.GRAY));
            sendChat(line);
            }

            MutableText line = Text.literal("\u2022 ").formatted(Formatting.DARK_GRAY)
                    .append(cmd)
                    .append(Text.literal(" - " + e.description).formatted(Formatting.GRAY));
            sendChat(line);
        }

        MutableText nav = Text.empty();
        if (p > 1) {
            nav.append(Text.literal("[Prev]").setStyle(Style.EMPTY
                    .withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand("/sw help " + (p - 1)))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Go to previous page").formatted(Formatting.GRAY)))
            ));
        } else {
            nav.append(Text.literal("[Prev]").formatted(Formatting.DARK_GRAY));
        }

        nav.append(Text.literal("  ").formatted(Formatting.DARK_GRAY));

        if (p < totalPages) {
            nav.append(Text.literal("[Next]").setStyle(Style.EMPTY
                    .withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand("/sw help " + (p + 1)))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Go to next page").formatted(Formatting.GRAY)))
            ));
        } else {
            nav.append(Text.literal("[Next]").formatted(Formatting.DARK_GRAY));
        }

        nav.append(Text.literal("  ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("(Tip: /skywave works too)").formatted(Formatting.DARK_GRAY));
        sendChat(nav);
        return 1;
    }

    public static MutableText prefix() {
        return Text.empty()
                .append(Text.literal("[Skywave]").setStyle(Style.EMPTY
                        .withColor(Formatting.AQUA)
                        .withBold(true)))
                .append(Text.literal(" ").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)));
    }

    public static void sendChat(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(text, false);
            }
        });
    }

    public static void sendFeedback(String msg) {
        sendChat(prefix().append(Text.literal(msg).formatted(Formatting.GRAY)));
    }
}

package org.wxter.skywave.client;

import net.fabricmc.api.ClientModInitializer;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.wxter.skywave.ModConstants;
//import org.wxter.skywave.client.gui.SkywaveYaclGui;
import org.wxter.skywave.client.gui.SkywaveMainScreen;
import org.wxter.skywave.config.SkywaveConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.wxter.skywave.client.RainOverlayRenderer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SkywaveClient implements ClientModInitializer {

    private static KeyBinding openGuiKey;
    // Флаг: открыть GUI в следующем тике клиента
    private static volatile boolean openGuiNextTick = false;

    @Override
    public void onInitializeClient() {
        SkywaveConfig.load();
        ModConstants.LOGGER.info("Skywave Client Initialized");

        HudRenderCallback.EVENT.register(RainOverlayRenderer::render);

        HuntingProfitTracker.INSTANCE.init();

        // Регистрация логики тика (включая queued-open)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Обработка queued команды на открытие GUI
            if (openGuiNextTick) {
                openGuiNextTick = false; // сбросим флаг сразу
                if (client != null && client.player != null) {
                    ModConstants.LOGGER.info("Opening Skywave GUI from queued command (next tick)");
                    client.setScreen(new SkywaveMainScreen(client.currentScreen));
                } else {
                    ModConstants.LOGGER.warn("Queued GUI open requested but client/player was null");
                }
            }

            // Tick handler для напоминаний о дожде
            RainReminderHandler.tick(client);
        });

        // Регистрация клиентских команд
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("skywave")
                    .executes(ctx -> {
                        ModConstants.LOGGER.info("/skywave command executed -> queueing GUI open for next tick");
                        openGuiNextTick = true;
                        return 1;
                    })
                    .then(literal("rainreminder")
                            .then(literal("on").executes(ctx -> {
                                SkywaveConfig.get().rainReminderEnabled = true;
                                sendFeedback("Rain Reminder is now ON");
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                SkywaveConfig.get().rainReminderEnabled = false;
                                sendFeedback("Rain Reminder is now OFF");
                                return 1;
                            }))
                    )
            );

            // alias /sw
            dispatcher.register(literal("sw")
                    .executes(ctx -> {
                        ModConstants.LOGGER.info("/sw command executed -> queueing GUI open for next tick");
                        openGuiNextTick = true;
                        return 1;
                    })
                    .then(literal("rainreminder")
                            .then(literal("on").executes(ctx -> {
                                SkywaveConfig.get().rainReminderEnabled = true;
                                sendFeedback("Rain Reminder is now ON");
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                SkywaveConfig.get().rainReminderEnabled = false;
                                sendFeedback("Rain Reminder is now OFF");
                                return 1;
                            }))
                    )
            );
        });
    }

    private static void sendFeedback(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("[Skywave] " + msg).formatted(Formatting.AQUA),
                        false
                );
            }
        });
    }
}
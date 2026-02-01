// src/main/java/org/wxter/skywave/client/SkywaveClient.java
package org.wxter.skywave.client;

import net.fabricmc.api.ClientModInitializer;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;
import org.wxter.skywave.client.gui.SkywaveHudMoveScreen;
import org.wxter.skywave.client.gui.SkywaveMainScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.wxter.skywave.config.SkywaveConfig;
import org.wxter.skywave.ModConstants;
import org.wxter.skywave.client.RainOverlayRenderer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SkywaveClient implements ClientModInitializer {

    private static volatile boolean openGuiNextTick = false;

    @Override
    public void onInitializeClient() {
        SkywaveConfig.load();
        ModConstants.LOGGER.info("Skywave Client Initialized");

        // Rain overlay remains (your existing)
        HudRenderCallback.EVENT.register(RainOverlayRenderer::render);

        // init tracker (внутри он зарегистрирует слушатель и HUD)
        HuntingProfitTracker.INSTANCE.init();

        //регистрируем lambda, которая вызывает onHudRender
        HudRenderCallback.EVENT.register((drawContext, tick) -> HuntingProfitTracker.INSTANCE.onHudRender(drawContext));

        // tick handler for queued GUI open
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiNextTick) {
                openGuiNextTick = false;
                if (client != null && client.player != null) {
                    client.setScreen(new SkywaveMainScreen(client.currentScreen));
                } else {
                    ModConstants.LOGGER.warn("Queued GUI open requested but client/player was null");
                }
            }
            // other tick tasks if needed
        });

        // commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("skywave")
                    .executes(ctx -> {
                        openGuiNextTick = true;
                        return 1;
                    })
                    .then(literal("gui").executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.execute(() -> mc.setScreen(new SkywaveHudMoveScreen(mc.currentScreen)));
                        return 1;
                    }))
            );

            dispatcher.register(literal("sw")
                    .executes(ctx -> {
                        openGuiNextTick = true;
                        return 1;
                    })
                    .then(literal("gui").executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.execute(() -> mc.setScreen(new SkywaveHudMoveScreen(mc.currentScreen)));
                        return 1;
                    }))
            );
        });
    }

    public static void sendFeedback(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[Skywave] " + msg).formatted(Formatting.AQUA), false);
            }
        });
    }
}
package org.wxter.skywave.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class NightSquidAlertHandler {

    private static final String NIGHT_SQUID_SPAWN_MESSAGE = "Pitch darkness reveals a Night Squid.";

    private NightSquidAlertHandler() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message == null) return;

            String plain = stripColorCodes(message.getString()).trim();
            if (!NIGHT_SQUID_SPAWN_MESSAGE.equals(plain)) return;

            RainOverlayRenderer.show(Text.literal("Night Squid"), 60);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.getSoundManager().play(
                    PositionedSoundInstance.master(
                            SoundEvents.BLOCK_ANVIL_LAND,
                            1f
                    )
            );
        });
    }

    private static String stripColorCodes(String s) {
        return s == null ? "" : s.replaceAll("\\u00A7.", "");
    }
}

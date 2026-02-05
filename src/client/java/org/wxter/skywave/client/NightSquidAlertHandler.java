package org.wxter.skywave.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.wxter.skywave.config.SkywaveConfig;

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
                            resolveAlertSound(SkywaveConfig.get().nightSquidAlertSound),
                            1f
                    )
            );
        });
    }

    private static SoundEvent resolveAlertSound(SkywaveConfig.NightSquidAlertSound soundType) {
        if (soundType == null) return SoundEvents.BLOCK_ANVIL_LAND;
        return switch (soundType) {
            case BELL -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case PLING -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case ORB -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case ANVIL -> SoundEvents.BLOCK_ANVIL_LAND;
        };
    }

    private static String stripColorCodes(String s) {
        return s == null ? "" : s.replaceAll("\\u00A7.", "");
    }
}

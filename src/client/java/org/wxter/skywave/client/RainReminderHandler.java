package org.wxter.skywave.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.wxter.skywave.config.SkywaveConfig;

public class RainReminderHandler {

    private static boolean wasRaining = false;

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (!SkywaveConfig.get().rainReminderEnabled) return;

        boolean isRaining = client.world.isRaining();

        if (wasRaining && !isRaining) {
            // CHAT уведомление
            if (SkywaveConfig.get().rainReminderType == SkywaveConfig.RainReminderType.CHAT) {
                client.player.sendMessage(
                        Text.literal("[Skywave] Rain is over, go to Vanessa!").formatted(Formatting.AQUA),
                        false
                );
            }

            // ONSCREEN уведомление
            if (SkywaveConfig.get().rainReminderType == SkywaveConfig.RainReminderType.ONSCREEN) {
                RainOverlayRenderer.show(Text.literal("Rain was ended!"), 240);
            }

            // Звук
            if (SkywaveConfig.get().rainReminderSound) {
                client.getSoundManager().play(
                        PositionedSoundInstance.master(
                                SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
                                0f
                        )
                );
            }
        }

        wasRaining = isRaining;
    }
}
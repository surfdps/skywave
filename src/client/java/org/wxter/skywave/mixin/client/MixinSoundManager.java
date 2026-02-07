package org.wxter.skywave.mixin.client;

import java.util.Set;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.wxter.skywave.config.SkywaveConfig;

@Mixin(SoundManager.class)
public class MixinSoundManager {
    private static final Set<String> MUTED_DRAGON_SOUND_PATHS = Set.of(
            "mob.enderdragon.growl",
            "mob.enderdragon.wings"
    );

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void skywave$muteEnderDragonSounds(SoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!SkywaveConfig.get().muteEnderDragonSounds) return;

        Identifier id = sound.getId();
        if (id == null) return;

        if (MUTED_DRAGON_SOUND_PATHS.contains(id.getPath())
                || MUTED_DRAGON_SOUND_PATHS.contains(id.toString())) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}

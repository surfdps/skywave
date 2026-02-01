package org.wxter.skywave.mixin.client;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wxter.skywave.client.EntityHighlightHelper;
import org.wxter.skywave.config.SkywaveConfig;

@Mixin(Entity.class)
public abstract class MixinEntityOutlineColor {

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void skywave$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!EntityHighlightHelper.matchesNametags(self)) return;
        cir.setReturnValue(SkywaveConfig.get().mobHighlightColor);
        cir.cancel();
    }
}

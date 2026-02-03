package org.wxter.skywave.mixin.client;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wxter.skywave.client.tracker.HuntingProfitTracker;

@Mixin(Mouse.class)
public class MixinMouse {
    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void skywave$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        HuntingProfitTracker.INSTANCE.onMouseScroll(vertical);
    }
}


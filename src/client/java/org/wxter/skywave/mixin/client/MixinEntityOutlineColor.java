package org.wxter.skywave.mixin.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wxter.skywave.config.SkywaveConfig;

@Mixin(Entity.class)
public abstract class MixinEntityOutlineColor {

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void skywave$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity)(Object)this;

        if (!SkywaveConfig.get().nightSquidHighlight) return;
        if (!(self instanceof SquidEntity)) return;

        String name = self.getName().getString();
        if (!name.contains("Night Squid")) return;

        // Читаем ARGB из конфига (уже int)
        int color = SkywaveConfig.get().nightSquidColor;
        cir.setReturnValue(color);
        cir.cancel();
    }
}
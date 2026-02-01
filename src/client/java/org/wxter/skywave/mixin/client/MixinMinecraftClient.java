package org.wxter.skywave.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wxter.skywave.client.EntityHighlightHelper;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Unique private static final Map<UUID, Boolean> skywave$visibilityCache = new HashMap<>();
    @Unique private static int skywave$tickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void skywave$tickCounter(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        skywave$tickCounter++;
        if (skywave$tickCounter > 4) { // каждые 4 тика обновляем
            skywave$visibilityCache.clear();
            skywave$tickCounter = 0;
        }
    }

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void skywave$mobHighlightOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!EntityHighlightHelper.matchesNametags(entity)) return;

        Boolean cached = skywave$visibilityCache.get(entity.getUuid());
        if (cached != null) {
            if (cached) {
                cir.setReturnValue(true);
                cir.cancel();
            }
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (player.squaredDistanceTo(entity) > 80 * 80) {
            skywave$visibilityCache.put(entity.getUuid(), false);
            return;
        }

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        Vec3d lookVec = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        Vec3d toEntity = entity.getBoundingBox().getCenter().subtract(camPos).normalize();

        double dot = MathHelper.clamp(lookVec.dotProduct(toEntity), -1.0, 1.0);
        double angle = Math.acos(dot) * (180.0 / Math.PI);
        double fov = mc.options.getFov().getValue();

        if (angle > fov / 2.0) {
            skywave$visibilityCache.put(entity.getUuid(), false);
            return;
        }

        HitResult hit = mc.world.raycast(new RaycastContext(
                camPos,
                entity.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        boolean visible = hit.getType() == HitResult.Type.MISS;
        skywave$visibilityCache.put(entity.getUuid(), visible);

        if (visible) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
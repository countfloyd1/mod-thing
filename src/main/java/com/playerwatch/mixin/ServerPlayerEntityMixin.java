package com.playerwatch.mixin;

import com.playerwatch.common.PlayerState;
import com.playerwatch.PlayerWatchMod;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void playerwatch_onTick(CallbackInfo ci) {
        // Server-side idle tracking could go here in a more advanced version
        // For now the client self-reports its own state
    }
}

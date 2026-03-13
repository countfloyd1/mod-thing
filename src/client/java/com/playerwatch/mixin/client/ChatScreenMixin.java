package com.playerwatch.mixin.client;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into ChatScreen open/close — the actual state detection is handled
 * in the tick loop in PlayerWatchClient, so this mixin is intentionally minimal.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void playerwatch_onChatOpen(CallbackInfo ci) {
        // State change picked up by client tick in PlayerWatchClient
    }
}

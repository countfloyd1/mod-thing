package com.playerwatch.mixin.client;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;

/**
 * No injections needed - state detection handled purely
 * by tick loop in PlayerWatchClient checking currentScreen
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    // intentionally empty - tick loop handles everything
}

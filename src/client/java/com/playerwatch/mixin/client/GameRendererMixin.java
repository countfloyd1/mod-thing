package com.playerwatch.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder for any future GameRenderer hooks (e.g. HUD overlays).
 * Currently, rendering is handled via WorldRenderEvents in PlayerWatchClient.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // Reserved for future HUD overlay injection
}

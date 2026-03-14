package com.playerwatch.client;

import com.playerwatch.PlayerWatchMod;
import com.playerwatch.common.PlayerState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PlayerWatchClient implements ClientModInitializer {

    // Track last known positions for idle detection
    private static final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private static final Map<UUID, Integer> idleTicks = new HashMap<>();
    private static final int IDLE_THRESHOLD = 200; // ~10 seconds
    private static int dotAnimTick = 0;

    @Override
    public void onInitializeClient() {
        PlayerWatchMod.LOGGER.info("PlayerWatch (client-only mode) initializing...");

        // Tick loop: update idle tracking for all visible players
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            dotAnimTick++;

            for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                if (player == client.player) continue;
                UUID uuid = player.getUuid();
                Vec3d currentPos = player.getPos();
                Vec3d lastPos = lastPositions.get(uuid);

                if (lastPos == null || !currentPos.equals(lastPos)) {
                    lastPositions.put(uuid, currentPos);
                    idleTicks.put(uuid, 0);
                } else {
                    idleTicks.merge(uuid, 1, Integer::sum);
                }
            }

            // Clean up disconnected players
            idleTicks.keySet().removeIf(uuid ->
                client.world.getPlayers().stream().noneMatch(p -> p.getUuid().equals(uuid))
            );
            lastPositions.keySet().removeIf(uuid ->
                client.world.getPlayers().stream().noneMatch(p -> p.getUuid().equals(uuid))
            );
        });

        HudRenderCallback.EVENT.register(PlayerWatchClient::renderLabels);
    }

    private static void renderLabels(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.options.hudHidden) return;

        float tickDelta = tickCounter.getTickDelta(true);
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;

            String label = getLabel(player);
            if (label == null) continue;
            int color = getColor(player);

            double px = MathHelper.lerp(tickDelta, player.prevX, player.getX());
            double py = MathHelper.lerp(tickDelta, player.prevY, player.getY()) + player.getHeight() + 0.5;
            double pz = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

            Vec3d screenPos = projectToScreen(client, new Vec3d(px, py, pz), tickDelta, screenW, screenH);
            if (screenPos == null) continue;

            double dist = client.player.getPos().distanceTo(new Vec3d(px, py, pz));
            if (dist > 32) continue;

            float scale = (float) MathHelper.clamp(1.0 - (dist / 48.0), 0.4, 1.0);
            int sx = (int) screenPos.x;
            int sy = (int) screenPos.y;
            int textWidth = client.textRenderer.getWidth(label);

            context.getMatrices().push();
            context.getMatrices().translate(sx, sy, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.fill(-textWidth / 2 - 2, -2, textWidth / 2 + 2, 10, 0x60000000);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), 0, 0, color);
            context.getMatrices().pop();
        }
    }

    private static String getLabel(AbstractClientPlayerEntity player) {
        UUID uuid = player.getUuid();
        int idle = idleTicks.getOrDefault(uuid, 0);

        if (player.isSleeping()) return "😴 sleeping";
        if (player.getPose() == EntityPose.CROUCHING) return "🤫 sneaking";
        if (idle >= IDLE_THRESHOLD) {
            int dots = (dotAnimTick / 10 % 3) + 1;
            return "💤 afk" + ".".repeat(dots);
        }
        return null; // normal, show nothing
    }

    private static int getColor(AbstractClientPlayerEntity player) {
        UUID uuid = player.getUuid();
        int idle = idleTicks.getOrDefault(uuid, 0);
        if (player.isSleeping()) return 0xAAAAAA;
        if (player.getPose() == EntityPose.CROUCHING) return 0xFFCC55;
        if (idle >= IDLE_THRESHOLD) return 0xCCCCCC;
        return 0xFFFFFF;
    }

    private static Vec3d projectToScreen(MinecraftClient client, Vec3d worldPos, float tickDelta, int screenW, int screenH) {
        double camX = MathHelper.lerp(tickDelta, client.player.prevX, client.player.getX());
        double camY = MathHelper.lerp(tickDelta, client.player.prevY, client.player.getY()) + client.player.getEyeHeight(client.player.getPose());
        double camZ = MathHelper.lerp(tickDelta, client.player.prevZ, client.player.getZ());

        double dx = worldPos.x - camX;
        double dy = worldPos.y - camY;
        double dz = worldPos.z - camZ;

        float yaw = (float) Math.toRadians(MathHelper.lerp(tickDelta, client.player.prevYaw, client.player.getYaw()));
        float pitch = (float) Math.toRadians(MathHelper.lerp(tickDelta, client.player.prevPitch, client.player.getPitch()));

        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);

        double rx = dx * cosYaw - dz * sinYaw;
        double ry = dy * cosPitch + (dx * sinYaw + dz * cosYaw) * sinPitch;
        double rz = -dx * sinYaw * cosPitch - dz * cosYaw * cosPitch + dy * sinPitch;

        if (rz <= 0.1) return null;

        double fov = Math.toRadians(client.options.getFov().getValue());
        double aspect = (double) screenW / screenH;
        double projX = (rx / (rz * Math.tan(fov / 2))) * (screenW / 2.0) + screenW / 2.0;
        double projY = -(ry / (rz * Math.tan(fov / 2) / aspect)) * (screenH / 2.0) + screenH / 2.0;

        if (projX < -100 || projX > screenW + 100 || projY < -100 || projY > screenH + 100) return null;
        return new Vec3d(projX, projY, rz);
    }
}

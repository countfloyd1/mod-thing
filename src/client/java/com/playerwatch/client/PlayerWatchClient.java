package com.playerwatch.client;

import com.playerwatch.PlayerWatchMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PlayerWatchClient implements ClientModInitializer {

    private static final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private static final Map<UUID, Integer> idleTicks = new HashMap<>();
    private static final int IDLE_THRESHOLD = 200;
    private static int dotAnimTick = 0;

    @Override
    public void onInitializeClient() {
        PlayerWatchMod.LOGGER.info("PlayerWatch (client-only mode) initializing...");

        // Clear state on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastPositions.clear();
            idleTicks.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            dotAnimTick++;

            // Use entity iterator instead of getPlayers()
            ArrayList<AbstractClientPlayerEntity> players = new ArrayList<>();
            for (var entity : client.world.getEntities()) {
                if (entity instanceof AbstractClientPlayerEntity p && p != client.player) {
                    players.add(p);
                }
            }

            for (AbstractClientPlayerEntity player : players) {
                UUID uuid = player.getUuid();
                Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
                Vec3d lastPos = lastPositions.get(uuid);

                if (lastPos == null || !currentPos.equals(lastPos)) {
                    lastPositions.put(uuid, currentPos);
                    idleTicks.put(uuid, 0);
                } else {
                    idleTicks.merge(uuid, 1, Integer::sum);
                }
            }
        });

        HudRenderCallback.EVENT.register(PlayerWatchClient::renderLabels);
    }

    private static void renderLabels(DrawContext context, RenderTickCounter tickCounter) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.world == null || client.player == null) return;
    if (client.options.hudHidden) return;

    int screenW = client.getWindow().getScaledWidth();
    int screenH = client.getWindow().getScaledHeight();

    int playerCount = 0;
    for (var entity : client.world.getEntities()) {
        if (entity instanceof AbstractClientPlayerEntity p && p != client.player) {
            playerCount++;
            String label = getLabel(p);

            // Draw all nearby players in a list on the left side of screen
            double dist = new Vec3d(p.getX(), p.getY(), p.getZ())
                .distanceTo(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()));
            if (dist > 32) continue;

            String debugLabel = p.getName().getString() + ": " + (label != null ? label : "normal") + " (" + (int)dist + "m)";
            context.drawTextWithShadow(client.textRenderer, Text.literal(debugLabel), 5, 5 + (playerCount * 10), 0xFFFFFF);
        }
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
        return null;
    }

    private static int getColor(AbstractClientPlayerEntity player) {
        UUID uuid = player.getUuid();
        int idle = idleTicks.getOrDefault(uuid, 0);
        if (player.isSleeping()) return 0xAAAAAA;
        if (player.getPose() == EntityPose.CROUCHING) return 0xFFCC55;
        if (idle >= IDLE_THRESHOLD) return 0xCCCCCC;
        return 0xFFFFFF;
    }

    private static Vec3d projectToScreen(MinecraftClient client, Vec3d worldPos, Vec3d camPos, int screenW, int screenH) {
        double dx = worldPos.x - camPos.x;
        double dy = worldPos.y - camPos.y;
        double dz = worldPos.z - camPos.z;

        float yaw = (float) Math.toRadians(client.player.getYaw());
        float pitch = (float) Math.toRadians(client.player.getPitch());

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

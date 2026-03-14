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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PlayerWatchClient implements ClientModInitializer {

    private static final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private static final Map<UUID, Integer> idleTicks = new HashMap<>();
    private static final int IDLE_THRESHOLD = 200;
    private static int dotAnimTick = 0;
    private static Method getPlayersMethod = null;

    @SuppressWarnings("unchecked")
    private static List<AbstractClientPlayerEntity> getPlayers(MinecraftClient client) {
        if (client.world == null) return new ArrayList<>();
        if (getPlayersMethod == null) {
            for (Method m : client.world.getClass().getMethods()) {
                try {
                    if (m.getParameterCount() == 0) {
                        Object result = m.invoke(client.world);
                        if (result instanceof List<?> list && !list.isEmpty()
                                && list.get(0) instanceof AbstractClientPlayerEntity) {
                            getPlayersMethod = m;
                            PlayerWatchMod.LOGGER.info("PlayerWatch: found players method: " + m.getName());
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (getPlayersMethod != null) {
            try {
                return (List<AbstractClientPlayerEntity>) getPlayersMethod.invoke(client.world);
            } catch (Exception e) {
                getPlayersMethod = null;
            }
        }
        // Fallback: scan all entities
        List<AbstractClientPlayerEntity> result = new ArrayList<>();
        try {
            for (var entity : client.world.getEntities()) {
                if (entity instanceof AbstractClientPlayerEntity p) result.add(p);
            }
        } catch (Exception ignored) {}
        return result;
    }

    @Override
    public void onInitializeClient() {
        PlayerWatchMod.LOGGER.info("PlayerWatch (client-only mode) initializing...");

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastPositions.clear();
            idleTicks.clear();
            getPlayersMethod = null;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            dotAnimTick++;

            List<AbstractClientPlayerEntity> players = getPlayers(client);

            for (AbstractClientPlayerEntity player : players) {
                if (player == client.player) continue;
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

            HashSet<UUID> uuids = new HashSet<>();
            for (AbstractClientPlayerEntity p : players) uuids.add(p.getUuid());
            idleTicks.keySet().retainAll(uuids);
            lastPositions.keySet().retainAll(uuids);
        });

        HudRenderCallback.EVENT.register(PlayerWatchClient::renderLabels);
    }

    private static void renderLabels(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.gameRenderer == null) return;
        if (client.options.hudHidden) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = new Vec3d(camera.getX(), camera.getY(), camera.getZ());

        for (AbstractClientPlayerEntity player : getPlayers(client)) {
            if (player == client.player) continue;

            String label = getLabel(player);
            if (label == null) continue;
            int color = getColor(player);

            double px = player.getX();
            double py = player.getY() + player.getHeight() + 0.5;
            double pz = player.getZ();

            Vec3d screenPos = projectToScreen(client, camera, new Vec3d(px, py, pz), camPos, screenW, screenH);
            if (screenPos == null) continue;

            double dist = camPos.distanceTo(new Vec3d(px, py, pz));
            if (dist > 32) continue;

            int sx = (int) screenPos.x;
            int sy = (int) screenPos.y;
            int textWidth = client.textRenderer.getWidth(label);

            context.fill(sx - textWidth / 2 - 2, sy - 2, sx + textWidth / 2 + 2, sy + 10, 0x60000000);
            context.drawText(client.textRenderer, Text.literal(label), sx - textWidth / 2, sy, color, true);
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

    private static Vec3d projectToScreen(MinecraftClient client, Camera camera, Vec3d worldPos, Vec3d camPos, int screenW, int screenH) {
        double dx = worldPos.x - camPos.x;
        double dy = worldPos.y - camPos.y;
        double dz = worldPos.z - camPos.z;

        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());

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

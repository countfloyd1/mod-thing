package com.playerwatch.client;

import com.playerwatch.PlayerWatchMod;
import com.playerwatch.common.PlayerState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PlayerWatchClient implements ClientModInitializer {

    public static final Map<UUID, PlayerState> otherPlayerStates = new HashMap<>();
    private static PlayerState currentState = PlayerState.NORMAL;
    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;
    private static int idleTicks = 0;
    private static final int IDLE_THRESHOLD_TICKS = 200;
    private static int dotAnimTick = 0;

    @Override
    public void onInitializeClient() {
        PlayerWatchMod.LOGGER.info("PlayerWatch initializing on client...");

        ClientPlayNetworking.registerGlobalReceiver(
                PlayerWatchMod.StateBroadcastPayload.ID,
                (payload, context) -> {
                    otherPlayerStates.put(payload.playerUuid(), PlayerState.fromId(payload.stateId()));
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            dotAnimTick++;
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            float yaw = client.player.getYaw();
            float pitch = client.player.getPitch();
            boolean moved = (px != lastX || py != lastY || pz != lastZ || yaw != lastYaw || pitch != lastPitch);
            if (moved) {
                idleTicks = 0;
                lastX = px; lastY = py; lastZ = pz;
                lastYaw = yaw; lastPitch = pitch;
            } else {
                idleTicks++;
            }
            PlayerState newState;
            if (client.currentScreen instanceof ChatScreen) {
                newState = PlayerState.TYPING;
            } else if (client.currentScreen != null) {
                newState = PlayerState.IN_GUI;
            } else if (idleTicks >= IDLE_THRESHOLD_TICKS) {
                newState = PlayerState.IDLE;
            } else {
                newState = PlayerState.NORMAL;
            }
            if (newState != currentState) {
                currentState = newState;
                ClientPlayNetworking.send(new PlayerWatchMod.StateUpdatePayload(newState.id));
            }
        });

        HudRenderCallback.EVENT.register(PlayerWatchClient::renderHudLabels);
    }

    private static void renderHudLabels(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.options.hudHidden) return;

        float tickDelta = tickCounter.getTickDelta(true);
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            UUID uuid = player.getUuid();
            PlayerState state = otherPlayerStates.getOrDefault(uuid, PlayerState.NORMAL);
            if (state == PlayerState.NORMAL) continue;

            double px = MathHelper.lerp(tickDelta, player.prevX, player.getX());
            double py = MathHelper.lerp(tickDelta, player.prevY, player.getY()) + player.getHeight() + 0.5;
            double pz = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

            Vec3d screenPos = projectToScreen(client, new Vec3d(px, py, pz), tickDelta, screenWidth, screenHeight);
            if (screenPos == null) continue;

            double dist = client.player.getPos().distanceTo(new Vec3d(px, py, pz));
            if (dist > 32) continue;
            float scale = (float) MathHelper.clamp(1.0 - (dist / 48.0), 0.4, 1.0);

            String label = getLabel(state);
            int color = getColor(state);
            int textWidth = client.textRenderer.getWidth(label);
            int sx = (int) screenPos.x;
            int sy = (int) screenPos.y;

            context.getMatrices().push();
            context.getMatrices().translate(sx, sy, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.fill(-textWidth / 2 - 2, -2, textWidth / 2 + 2, 10, 0x60000000);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), 0, 0, color);
            context.getMatrices().pop();
        }
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

    private static String getLabel(PlayerState state) {
        return switch (state) {
            case TYPING -> "✏ typing" + ".".repeat((dotAnimTick / 10 % 3) + 1);
            case IN_GUI -> "📦 in menu";
            case IDLE -> "💤 idle";
            default -> "";
        };
    }

    private static int getColor(PlayerState state) {
        return switch (state) {
            case TYPING -> 0xFFFFAA;
            case IN_GUI -> 0xAADDFF;
            case IDLE -> 0xCCCCCC;
            default -> 0xFFFFFF;
        };
    }
}

package com.playerwatch.client;

import com.playerwatch.PlayerWatchMod;
import com.playerwatch.common.PlayerState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PlayerWatchClient implements ClientModInitializer {

    // Map of other players' states (UUID -> state)
    public static final Map<UUID, PlayerState> otherPlayerStates = new HashMap<>();

    // Our own current state
    private static PlayerState currentState = PlayerState.NORMAL;

    // Idle tracking
    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;
    private static int idleTicks = 0;
    private static final int IDLE_THRESHOLD_TICKS = 200; // ~10 seconds

    // Typing dot animation
    private static int dotAnimTick = 0;

    @Override
    public void onInitializeClient() {
        PlayerWatchMod.LOGGER.info("PlayerWatch initializing on client...");

        // Register S2C packet receiver
        ClientPlayNetworking.registerGlobalReceiver(
                PlayerWatchMod.StateBroadcastPayload.ID,
                (payload, context) -> {
                    otherPlayerStates.put(payload.playerUuid(), PlayerState.fromId(payload.stateId()));
                }
        );

        // Tick-based state tracking
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

            // Determine our state
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

            // Send update if state changed
            if (newState != currentState) {
                currentState = newState;
                ClientPlayNetworking.send(new PlayerWatchMod.StateUpdatePayload(newState.id));
            }
        });

        // Render indicators above player heads
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            renderPlayerIndicators(context);
        });
    }

    private void renderPlayerIndicators(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;

            UUID uuid = player.getUuid();
            PlayerState state = otherPlayerStates.getOrDefault(uuid, PlayerState.NORMAL);
            if (state == PlayerState.NORMAL) continue;

            // Get the indicator text and color
            Text indicator = getIndicatorText(state);
            if (indicator == null) continue;

            // Calculate world position above player's head
            Vec3d pos = player.getPos().add(0, player.getHeight() + 0.5, 0);
            Vec3d cameraPos = context.camera().getPos();

            matrices.push();
            matrices.translate(
                    pos.x - cameraPos.x,
                    pos.y - cameraPos.y,
                    pos.z - cameraPos.z
            );

            // Face camera
            matrices.multiply(context.camera().getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            // Draw the text using immediate mode
            var immediate = client.getBufferBuilders().getEntityVertexConsumers();
            int light = 0xF000F0;
            client.textRenderer.draw(
                    indicator,
                    -client.textRenderer.getWidth(indicator) / 2f,
                    0,
                    -1, // white
                    false,
                    matrices.peek().getPositionMatrix(),
                    immediate,
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,
                    0x40000000, // semi-transparent background
                    light
            );
            immediate.draw();

            matrices.pop();
        }
    }

    private Text getIndicatorText(PlayerState state) {
        return switch (state) {
            case TYPING -> {
                // Animated dots: ., .., ...
                int dots = (dotAnimTick / 10 % 3) + 1;
                String dotStr = ".".repeat(dots);
                yield Text.literal("✏ typing" + dotStr)
                        .withColor(0xFFFFAA); // yellow-ish
            }
            case IN_GUI -> Text.literal("📦 in menu")
                    .withColor(0xAADDFF); // light blue
            case IDLE -> Text.literal("💤 idle")
                    .withColor(0xBBBBBB); // grey
            default -> null;
        };
    }
}

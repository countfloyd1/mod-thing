package com.playerwatch;

import com.playerwatch.common.PlayerState;
import com.playerwatch.network.PlayerWatchPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerWatchMod implements ModInitializer {
    public static final String MOD_ID = "playerwatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Server-side store of player states
    public static final Map<UUID, PlayerState> playerStates = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("PlayerWatch initializing on server...");

        // Register the C2S payload type
        PayloadTypeRegistry.playC2S().register(StateUpdatePayload.ID, StateUpdatePayload.CODEC);

        // Register the S2C payload type
        PayloadTypeRegistry.playS2C().register(StateBroadcastPayload.ID, StateBroadcastPayload.CODEC);

        // Listen for state updates from clients
        ServerPlayNetworking.registerGlobalReceiver(StateUpdatePayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            PlayerState newState = PlayerState.fromId(payload.stateId());
            playerStates.put(sender.getUuid(), newState);

            // Broadcast to all other players on the server
            StateBroadcastPayload broadcast = new StateBroadcastPayload(sender.getUuid(), payload.stateId());
            for (ServerPlayerEntity other : context.player().getServer().getPlayerManager().getPlayerList()) {
                if (!other.getUuid().equals(sender.getUuid())) {
                    ServerPlayNetworking.send(other, broadcast);
                }
            }
        });

        // Clean up on disconnect
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerStates.remove(handler.player.getUuid());
            // Broadcast removal (state NORMAL = gone)
            StateBroadcastPayload broadcast = new StateBroadcastPayload(handler.player.getUuid(), PlayerState.NORMAL.id);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(other, broadcast);
            }
        });
    }

    // ── Payload records ──────────────────────────────────────────────────────

    public record StateUpdatePayload(int stateId) implements CustomPayload {
        public static final CustomPayload.Id<StateUpdatePayload> ID =
                new CustomPayload.Id<>(PlayerWatchPackets.STATE_UPDATE_C2S);
        public static final PacketCodec<PacketByteBuf, StateUpdatePayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeInt(value.stateId()),
                        buf -> new StateUpdatePayload(buf.readInt())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record StateBroadcastPayload(UUID playerUuid, int stateId) implements CustomPayload {
        public static final CustomPayload.Id<StateBroadcastPayload> ID =
                new CustomPayload.Id<>(PlayerWatchPackets.STATE_BROADCAST_S2C);
        public static final PacketCodec<PacketByteBuf, StateBroadcastPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeUuid(value.playerUuid()); buf.writeInt(value.stateId()); },
                        buf -> new StateBroadcastPayload(buf.readUuid(), buf.readInt())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
}

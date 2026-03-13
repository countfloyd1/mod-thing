package com.playerwatch.network;

import net.minecraft.util.Identifier;

public class PlayerWatchPackets {
    // Client -> Server: tell server our state
    public static final Identifier STATE_UPDATE_C2S = Identifier.of("playerwatch", "state_update_c2s");

    // Server -> Client: broadcast a player's state
    public static final Identifier STATE_BROADCAST_S2C = Identifier.of("playerwatch", "state_broadcast_s2c");
}

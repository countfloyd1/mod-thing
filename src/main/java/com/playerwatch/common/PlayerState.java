package com.playerwatch.common;

public enum PlayerState {
    NORMAL(0),
    TYPING(1),
    IN_GUI(2),
    IDLE(3);

    public final int id;

    PlayerState(int id) {
        this.id = id;
    }

    public static PlayerState fromId(int id) {
        for (PlayerState state : values()) {
            if (state.id == id) return state;
        }
        return NORMAL;
    }
}

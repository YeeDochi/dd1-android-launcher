package com.winlator.dd1;

public enum DD1HomeState {
    RUNTIME_MISSING,
    GAME_MISSING,
    PROFILE_MISSING,
    READY;

    public static DD1HomeState from(boolean runtimeReady, boolean gameReady, boolean profileReady) {
        if (!runtimeReady) return RUNTIME_MISSING;
        if (!gameReady) return GAME_MISSING;
        return profileReady ? READY : PROFILE_MISSING;
    }
}

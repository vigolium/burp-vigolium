package com.vigolium.extension.service;

import java.time.Instant;

public record BridgeStatus(State state, String message, String listenUrl, Instant startedAt, Instant lastCommandAt) {
    public enum State {
        DISABLED,
        STARTING,
        LISTENING,
        ERROR
    }

    public static BridgeStatus disabled() {
        return new BridgeStatus(State.DISABLED, "Bridge disabled", null, null, null);
    }
}

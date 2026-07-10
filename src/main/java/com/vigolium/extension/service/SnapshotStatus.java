package com.vigolium.extension.service;

import java.time.Instant;

public record SnapshotStatus(
        State state,
        String message,
        int discovered,
        int uploaded,
        int inserted,
        int updated,
        int unchanged,
        int failed,
        Instant completedAt,
        Instant nextRunAt) {

    public enum State {
        IDLE,
        RUNNING,
        SUCCESS,
        FAILED,
        DISABLED
    }

    public static SnapshotStatus idle() {
        return new SnapshotStatus(State.IDLE, "Not run yet", 0, 0, 0, 0, 0, 0, null, null);
    }
}

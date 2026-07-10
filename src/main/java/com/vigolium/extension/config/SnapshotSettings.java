package com.vigolium.extension.config;

public interface SnapshotSettings {
    boolean isSnapshotAutoEnabled();

    void setSnapshotAutoEnabled(boolean enabled);

    int getSnapshotIntervalMinutes();

    void setSnapshotIntervalMinutes(int minutes);

    boolean isSnapshotInScopeOnly();

    void setSnapshotInScopeOnly(boolean inScopeOnly);
}

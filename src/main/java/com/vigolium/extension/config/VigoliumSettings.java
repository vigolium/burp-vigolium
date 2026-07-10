package com.vigolium.extension.config;

public interface VigoliumSettings
        extends ServerSettings,
                ProxySettings,
                FilterSettings,
                HotkeySettings,
                ScanSettings,
                SnapshotSettings,
                BridgeSettings {
    void addChangeListener(Runnable listener);
}

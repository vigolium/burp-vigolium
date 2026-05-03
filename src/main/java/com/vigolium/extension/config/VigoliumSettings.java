package com.vigolium.extension.config;

public interface VigoliumSettings extends ServerSettings, ProxySettings, FilterSettings, HotkeySettings, ScanSettings {
    void addChangeListener(Runnable listener);
}

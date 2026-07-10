package com.vigolium.extension.config;

public interface HotkeySettings {
    String getIngestHotkey();

    void setIngestHotkey(String montoyaKey);

    String getScanHotkey();

    void setScanHotkey(String montoyaKey);

    String getAgentScanHotkey();

    void setAgentScanHotkey(String montoyaKey);

    String getSnapshotSitemapHotkey();

    void setSnapshotSitemapHotkey(String montoyaKey);
}

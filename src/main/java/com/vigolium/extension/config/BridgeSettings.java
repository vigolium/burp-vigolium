package com.vigolium.extension.config;

public interface BridgeSettings {
    boolean isBridgeEnabled();

    void setBridgeEnabled(boolean enabled);

    String getBridgeListenUrl();

    void setBridgeListenUrl(String url);

    boolean isBridgeInScopeOnly();

    void setBridgeInScopeOnly(boolean inScopeOnly);
}

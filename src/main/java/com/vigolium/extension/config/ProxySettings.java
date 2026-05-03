package com.vigolium.extension.config;

public interface ProxySettings {
    boolean isProxyEnabled();

    void setProxyEnabled(boolean enabled);

    boolean isInScopeOnly();

    void setInScopeOnly(boolean inScopeOnly);
}

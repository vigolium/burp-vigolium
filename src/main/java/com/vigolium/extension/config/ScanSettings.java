package com.vigolium.extension.config;

public interface ScanSettings {

    /** Comma-separated module IDs to run. Blank = scan all modules. */
    String getCustomModules();

    void setCustomModules(String customModules);

    /** Scan timeout as a Go duration string (e.g. "30s", "2m"). Blank = server default. */
    String getScanTimeout();

    void setScanTimeout(String timeout);
}

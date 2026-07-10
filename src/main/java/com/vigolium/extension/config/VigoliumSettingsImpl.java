package com.vigolium.extension.config;

import burp.api.montoya.persistence.Preferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vigolium.extension.filter.FilterRule;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VigoliumSettingsImpl implements VigoliumSettings {

    private static final String KEY_SERVER_URL = "vigolium.serverUrl";
    private static final String KEY_API_KEY = "vigolium.apiKey";
    private static final String KEY_PROXY_ENABLED = "vigolium.proxyEnabled";
    private static final String KEY_PROXY_FILTER_RULES = "vigolium.proxyFilterRules";
    private static final String KEY_IN_SCOPE_ONLY = "vigolium.inScopeOnly";
    private static final String KEY_HOTKEY_INGEST = "vigolium.hotkeyIngest";
    private static final String KEY_HOTKEY_SCAN = "vigolium.hotkeyScan";
    private static final String KEY_HOTKEY_AGENT_SCAN = "vigolium.hotkeyAgentScan";
    private static final String KEY_HOTKEY_SNAPSHOT_SITEMAP = "vigolium.hotkeySnapshotSitemap";
    private static final String KEY_CUSTOM_MODULES = "vigolium.customModules";
    private static final String KEY_SCAN_TIMEOUT = "vigolium.scanTimeout";
    private static final String KEY_SNAPSHOT_AUTO_ENABLED = "vigolium.snapshotAutoEnabled";
    private static final String KEY_SNAPSHOT_INTERVAL_MINUTES = "vigolium.snapshotIntervalMinutes";
    private static final String KEY_SNAPSHOT_IN_SCOPE_ONLY = "vigolium.snapshotInScopeOnly";
    private static final String KEY_BRIDGE_ENABLED = "vigolium.bridgeEnabled";
    private static final String KEY_BRIDGE_LISTEN_URL = "vigolium.bridgeListenUrl";
    private static final String KEY_BRIDGE_IN_SCOPE_ONLY = "vigolium.bridgeInScopeOnly";
    private static final String LEGACY_KEY_BRIDGE_LONG_POLL_URL = "vigolium.bridgeLongPollUrl";

    private static final Type FILTER_RULE_LIST_TYPE = new TypeToken<List<FilterRule>>() {}.getType();

    private final Preferences preferences;
    private final Gson gson;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public VigoliumSettingsImpl(Preferences preferences) {
        this.preferences = preferences;
        this.gson = new Gson();
    }

    // --- ServerSettings ---

    @Override
    public String getServerUrl() {
        String value = preferences.getString(KEY_SERVER_URL);
        return value != null ? value : "http://127.0.0.1:9002";
    }

    @Override
    public void setServerUrl(String url) {
        preferences.setString(KEY_SERVER_URL, url);
        notifyListeners();
    }

    @Override
    public String getApiKey() {
        String value = preferences.getString(KEY_API_KEY);
        return value != null ? value : "";
    }

    @Override
    public void setApiKey(String apiKey) {
        preferences.setString(KEY_API_KEY, apiKey);
        notifyListeners();
    }

    // --- ProxySettings ---

    @Override
    public boolean isProxyEnabled() {
        Boolean value = preferences.getBoolean(KEY_PROXY_ENABLED);
        return value != null ? value : false;
    }

    @Override
    public void setProxyEnabled(boolean enabled) {
        preferences.setBoolean(KEY_PROXY_ENABLED, enabled);
        notifyListeners();
    }

    @Override
    public boolean isInScopeOnly() {
        Boolean value = preferences.getBoolean(KEY_IN_SCOPE_ONLY);
        return value != null ? value : false;
    }

    @Override
    public void setInScopeOnly(boolean inScopeOnly) {
        preferences.setBoolean(KEY_IN_SCOPE_ONLY, inScopeOnly);
        notifyListeners();
    }

    // --- FilterSettings ---

    @Override
    public List<FilterRule> getProxyFilterRules() {
        String json = preferences.getString(KEY_PROXY_FILTER_RULES);
        if (json == null || json.isEmpty()) {
            return getDefaultFilterRules();
        }
        return gson.fromJson(json, FILTER_RULE_LIST_TYPE);
    }

    @Override
    public void setProxyFilterRules(List<FilterRule> rules) {
        preferences.setString(KEY_PROXY_FILTER_RULES, gson.toJson(rules));
        notifyListeners();
    }

    // --- HotkeySettings ---

    @Override
    public String getIngestHotkey() {
        String value = preferences.getString(KEY_HOTKEY_INGEST);
        return value != null ? value : "Ctrl+Alt+V";
    }

    @Override
    public void setIngestHotkey(String montoyaKey) {
        preferences.setString(KEY_HOTKEY_INGEST, montoyaKey);
        notifyListeners();
    }

    @Override
    public String getScanHotkey() {
        String value = preferences.getString(KEY_HOTKEY_SCAN);
        return value != null ? value : "Ctrl+Alt+N";
    }

    @Override
    public void setScanHotkey(String montoyaKey) {
        preferences.setString(KEY_HOTKEY_SCAN, montoyaKey);
        notifyListeners();
    }

    @Override
    public String getAgentScanHotkey() {
        String value = preferences.getString(KEY_HOTKEY_AGENT_SCAN);
        return value != null ? value : "Ctrl+Alt+A";
    }

    @Override
    public void setAgentScanHotkey(String montoyaKey) {
        preferences.setString(KEY_HOTKEY_AGENT_SCAN, montoyaKey);
        notifyListeners();
    }

    @Override
    public String getSnapshotSitemapHotkey() {
        String value = preferences.getString(KEY_HOTKEY_SNAPSHOT_SITEMAP);
        return value != null ? value : "Ctrl+Alt+S";
    }

    @Override
    public void setSnapshotSitemapHotkey(String montoyaKey) {
        preferences.setString(KEY_HOTKEY_SNAPSHOT_SITEMAP, montoyaKey);
        notifyListeners();
    }

    // --- ScanSettings ---

    @Override
    public String getCustomModules() {
        String value = preferences.getString(KEY_CUSTOM_MODULES);
        return value != null ? value : "";
    }

    @Override
    public void setCustomModules(String customModules) {
        preferences.setString(KEY_CUSTOM_MODULES, customModules != null ? customModules : "");
        notifyListeners();
    }

    @Override
    public String getScanTimeout() {
        String value = preferences.getString(KEY_SCAN_TIMEOUT);
        return value != null ? value : "";
    }

    @Override
    public void setScanTimeout(String timeout) {
        preferences.setString(KEY_SCAN_TIMEOUT, timeout != null ? timeout : "");
        notifyListeners();
    }

    // --- SnapshotSettings ---

    @Override
    public boolean isSnapshotAutoEnabled() {
        Boolean value = preferences.getBoolean(KEY_SNAPSHOT_AUTO_ENABLED);
        return value != null ? value : false;
    }

    @Override
    public void setSnapshotAutoEnabled(boolean enabled) {
        preferences.setBoolean(KEY_SNAPSHOT_AUTO_ENABLED, enabled);
        notifyListeners();
    }

    @Override
    public int getSnapshotIntervalMinutes() {
        Integer value = preferences.getInteger(KEY_SNAPSHOT_INTERVAL_MINUTES);
        return value != null && value > 0 ? value : 5;
    }

    @Override
    public void setSnapshotIntervalMinutes(int minutes) {
        preferences.setInteger(KEY_SNAPSHOT_INTERVAL_MINUTES, Math.max(1, minutes));
        notifyListeners();
    }

    @Override
    public boolean isSnapshotInScopeOnly() {
        Boolean value = preferences.getBoolean(KEY_SNAPSHOT_IN_SCOPE_ONLY);
        return value != null ? value : true;
    }

    @Override
    public void setSnapshotInScopeOnly(boolean inScopeOnly) {
        preferences.setBoolean(KEY_SNAPSHOT_IN_SCOPE_ONLY, inScopeOnly);
        notifyListeners();
    }

    // --- BridgeSettings ---

    @Override
    public boolean isBridgeEnabled() {
        Boolean value = preferences.getBoolean(KEY_BRIDGE_ENABLED);
        return value != null ? value : false;
    }

    @Override
    public void setBridgeEnabled(boolean enabled) {
        preferences.setBoolean(KEY_BRIDGE_ENABLED, enabled);
        notifyListeners();
    }

    @Override
    public String getBridgeListenUrl() {
        String value = preferences.getString(KEY_BRIDGE_LISTEN_URL);
        if (value == null) value = preferences.getString(LEGACY_KEY_BRIDGE_LONG_POLL_URL);
        return value != null ? value : "http://127.0.0.1:9009";
    }

    @Override
    public void setBridgeListenUrl(String url) {
        preferences.setString(KEY_BRIDGE_LISTEN_URL, url != null ? url.trim() : "");
        notifyListeners();
    }

    @Override
    public boolean isBridgeInScopeOnly() {
        Boolean value = preferences.getBoolean(KEY_BRIDGE_IN_SCOPE_ONLY);
        return value != null ? value : false;
    }

    @Override
    public void setBridgeInScopeOnly(boolean inScopeOnly) {
        preferences.setBoolean(KEY_BRIDGE_IN_SCOPE_ONLY, inScopeOnly);
        notifyListeners();
    }

    // --- Change Listeners ---

    @Override
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    // --- Defaults ---

    private static List<FilterRule> getDefaultFilterRules() {
        return FilterRule.getDefaultRules();
    }
}

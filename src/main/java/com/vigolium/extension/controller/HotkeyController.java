package com.vigolium.extension.controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import com.vigolium.extension.config.HotkeySettings;
import com.vigolium.extension.service.RequestDispatchService;
import java.util.List;
import java.util.function.Consumer;

public class HotkeyController {

    private final MontoyaApi api;
    private final HotkeySettings settings;
    private final RequestDispatchService dispatcher;
    private final Runnable snapshotAction;

    private Registration ingestRegistration;
    private Registration scanRegistration;
    private Registration agentScanRegistration;
    private Registration snapshotRegistration;

    public HotkeyController(MontoyaApi api, HotkeySettings settings, RequestDispatchService dispatcher) {
        this(api, settings, dispatcher, null);
    }

    public HotkeyController(
            MontoyaApi api, HotkeySettings settings, RequestDispatchService dispatcher, Runnable snapshotAction) {
        this.api = api;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.snapshotAction = snapshotAction;
    }

    public void register() {
        registerIngest();
        registerScan();
        registerAgentScan();
        registerSnapshot();
    }

    public void updateIngestHotkey(String montoyaKey) {
        settings.setIngestHotkey(montoyaKey);
        registerIngest();
    }

    public void updateScanHotkey(String montoyaKey) {
        settings.setScanHotkey(montoyaKey);
        registerScan();
    }

    public void updateAgentScanHotkey(String montoyaKey) {
        settings.setAgentScanHotkey(montoyaKey);
        registerAgentScan();
    }

    public void updateSnapshotSitemapHotkey(String montoyaKey) {
        settings.setSnapshotSitemapHotkey(montoyaKey);
        registerSnapshot();
    }

    public void shutdown() {
        unregisterIngest();
        unregisterScan();
        unregisterAgentScan();
        unregisterSnapshot();
    }

    private void registerIngest() {
        unregisterIngest();
        ingestRegistration = registerForAllContexts(
                settings.getIngestHotkey(),
                "Vigolium: Send to Ingestion",
                targets -> dispatcher.sendToIngestion(targets, "Hotkey"));
    }

    private void registerScan() {
        unregisterScan();
        scanRegistration = registerForAllContexts(
                settings.getScanHotkey(),
                "Vigolium: Send to Native Scan",
                targets -> dispatcher.sendToScan(targets, "Hotkey"));
    }

    private void registerAgentScan() {
        unregisterAgentScan();
        agentScanRegistration = registerForAllContexts(
                settings.getAgentScanHotkey(),
                "Vigolium: Send to Agentic Scan",
                targets -> dispatcher.sendToAgentScan(targets, "Hotkey"));
    }

    private void registerSnapshot() {
        unregisterSnapshot();
        String hotkey = settings.getSnapshotSitemapHotkey();
        if (snapshotAction == null || hotkey == null || hotkey.isBlank()) return;
        snapshotRegistration = api.userInterface()
                .registerHotKeyHandler(
                        HotKey.hotKey("Vigolium: Snapshot Target Site Map", hotkey), event -> snapshotAction.run());
    }

    private Registration registerForAllContexts(
            String hotkey, String name, Consumer<List<HttpRequestResponse>> action) {
        if (hotkey == null || hotkey.isBlank()) return null;

        HotKeyHandler handler = event -> {
            List<HttpRequestResponse> targets = RequestDispatchService.collectTargets(
                    event.selectedRequestResponses(), event.messageEditorRequestResponse());
            if (!targets.isEmpty()) {
                action.accept(targets);
            }
        };

        // The no-context overload is Burp's ALL_CONTEXTS registration. It covers HTTP message
        // editors and every supported request table without registering the same key more than once.
        return api.userInterface().registerHotKeyHandler(HotKey.hotKey(name, hotkey), handler);
    }

    private void unregisterIngest() {
        if (ingestRegistration != null) {
            ingestRegistration.deregister();
            ingestRegistration = null;
        }
    }

    private void unregisterScan() {
        if (scanRegistration != null) {
            scanRegistration.deregister();
            scanRegistration = null;
        }
    }

    private void unregisterAgentScan() {
        if (agentScanRegistration != null) {
            agentScanRegistration.deregister();
            agentScanRegistration = null;
        }
    }

    private void unregisterSnapshot() {
        if (snapshotRegistration != null) {
            snapshotRegistration.deregister();
            snapshotRegistration = null;
        }
    }
}

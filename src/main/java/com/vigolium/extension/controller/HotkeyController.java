package com.vigolium.extension.controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import com.vigolium.extension.config.HotkeySettings;
import com.vigolium.extension.service.RequestDispatchService;
import java.util.List;

public class HotkeyController {

    private final MontoyaApi api;
    private final HotkeySettings settings;
    private final RequestDispatchService dispatcher;

    private Registration ingestRegistration;
    private Registration scanRegistration;
    private Registration agentScanRegistration;

    public HotkeyController(MontoyaApi api, HotkeySettings settings, RequestDispatchService dispatcher) {
        this.api = api;
        this.settings = settings;
        this.dispatcher = dispatcher;
    }

    public void register() {
        registerIngest();
        registerScan();
        registerAgentScan();
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

    public void shutdown() {
        unregisterIngest();
        unregisterScan();
        unregisterAgentScan();
    }

    private void registerIngest() {
        unregisterIngest();
        String hotkey = settings.getIngestHotkey();
        if (hotkey == null || hotkey.isBlank()) return;

        ingestRegistration = api.userInterface()
                .registerHotKeyHandler(HotKey.hotKey("Vigolium: Send to Ingestion", hotkey), event -> {
                    List<HttpRequestResponse> targets = RequestDispatchService.collectTargets(
                            event.selectedRequestResponses(), event.messageEditorRequestResponse());
                    if (!targets.isEmpty()) {
                        dispatcher.sendToIngestion(targets, "Hotkey");
                    }
                });
    }

    private void registerScan() {
        unregisterScan();
        String hotkey = settings.getScanHotkey();
        if (hotkey == null || hotkey.isBlank()) return;

        scanRegistration = api.userInterface()
                .registerHotKeyHandler(HotKey.hotKey("Vigolium: Send to Native Scan", hotkey), event -> {
                    List<HttpRequestResponse> targets = RequestDispatchService.collectTargets(
                            event.selectedRequestResponses(), event.messageEditorRequestResponse());
                    if (!targets.isEmpty()) {
                        dispatcher.sendToScan(targets, "Hotkey");
                    }
                });
    }

    private void registerAgentScan() {
        unregisterAgentScan();
        String hotkey = settings.getAgentScanHotkey();
        if (hotkey == null || hotkey.isBlank()) return;

        agentScanRegistration = api.userInterface()
                .registerHotKeyHandler(HotKey.hotKey("Vigolium: Send to Agentic Scan", hotkey), event -> {
                    List<HttpRequestResponse> targets = RequestDispatchService.collectTargets(
                            event.selectedRequestResponses(), event.messageEditorRequestResponse());
                    if (!targets.isEmpty()) {
                        dispatcher.sendToAgentScan(targets, "Hotkey");
                    }
                });
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
}

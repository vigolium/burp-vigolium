package com.vigolium.extension.controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import com.vigolium.extension.config.HotkeySettings;
import com.vigolium.extension.service.RequestDispatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HotkeyController {

    /**
     * Contexts the hotkeys are registered in. The no-context {@code registerHotKeyHandler(HotKey,
     * handler)} overload only binds {@link HotKeyContext#HTTP_MESSAGE_EDITOR}, so selecting rows in
     * the Proxy history or Target site map would never trigger the handler. Registering across every
     * context makes the hotkeys work from table selections too.
     */
    private static final List<HotKeyContext> HOTKEY_CONTEXTS = List.of(
            HotKeyContext.HTTP_MESSAGE_EDITOR,
            HotKeyContext.PROXY_HTTP_HISTORY,
            HotKeyContext.SITE_MAP_CONTENTS_TABLE,
            HotKeyContext.INTRUDER_ATTACK_RESULTS,
            HotKeyContext.ORGANIZER_ENTRIES);

    private final MontoyaApi api;
    private final HotkeySettings settings;
    private final RequestDispatchService dispatcher;

    private List<Registration> ingestRegistrations = new ArrayList<>();
    private List<Registration> scanRegistrations = new ArrayList<>();
    private List<Registration> agentScanRegistrations = new ArrayList<>();

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
        ingestRegistrations = registerAcrossContexts(
                settings.getIngestHotkey(),
                "Vigolium: Send to Ingestion",
                targets -> dispatcher.sendToIngestion(targets, "Hotkey"));
    }

    private void registerScan() {
        unregisterScan();
        scanRegistrations = registerAcrossContexts(
                settings.getScanHotkey(),
                "Vigolium: Send to Native Scan",
                targets -> dispatcher.sendToScan(targets, "Hotkey"));
    }

    private void registerAgentScan() {
        unregisterAgentScan();
        agentScanRegistrations = registerAcrossContexts(
                settings.getAgentScanHotkey(),
                "Vigolium: Send to Agentic Scan",
                targets -> dispatcher.sendToAgentScan(targets, "Hotkey"));
    }

    private List<Registration> registerAcrossContexts(
            String hotkey, String name, Consumer<List<HttpRequestResponse>> action) {
        List<Registration> registrations = new ArrayList<>();
        if (hotkey == null || hotkey.isBlank()) return registrations;

        HotKeyHandler handler = event -> {
            List<HttpRequestResponse> targets = RequestDispatchService.collectTargets(
                    event.selectedRequestResponses(), event.messageEditorRequestResponse());
            if (!targets.isEmpty()) {
                action.accept(targets);
            }
        };

        for (HotKeyContext context : HOTKEY_CONTEXTS) {
            registrations.add(api.userInterface().registerHotKeyHandler(context, HotKey.hotKey(name, hotkey), handler));
        }
        return registrations;
    }

    private void unregisterIngest() {
        deregisterAll(ingestRegistrations);
    }

    private void unregisterScan() {
        deregisterAll(scanRegistrations);
    }

    private void unregisterAgentScan() {
        deregisterAll(agentScanRegistrations);
    }

    private static void deregisterAll(List<Registration> registrations) {
        for (Registration registration : registrations) {
            if (registration != null) {
                registration.deregister();
            }
        }
        registrations.clear();
    }
}

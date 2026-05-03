package com.vigolium.extension.controller;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import com.vigolium.extension.service.RequestDispatchService;
import java.awt.Component;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

public class ContextMenuController implements ContextMenuItemsProvider {

    private final RequestDispatchService dispatcher;

    public ContextMenuController(RequestDispatchService dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> items = RequestDispatchService.collectTargets(
                event.selectedRequestResponses(), event.messageEditorRequestResponse());

        if (items.isEmpty()) {
            return List.of();
        }

        JMenu menu = new JMenu("Vigolium");
        menu.setName("vigoliumMenu");

        JMenuItem ingestItem = new JMenuItem("Send to Ingestion");
        ingestItem.setName("sendToIngestionMenuItem");
        ingestItem.addActionListener(e -> dispatcher.sendToIngestion(items, "CtxMenu"));

        JMenuItem scanItem = new JMenuItem("Send to Native Scan");
        scanItem.setName("sendToScanMenuItem");
        scanItem.addActionListener(e -> dispatcher.sendToScan(items, "CtxMenu"));

        JMenuItem agentScanItem = new JMenuItem("Send to Agentic Scan");
        agentScanItem.setName("sendToAgentScanMenuItem");
        agentScanItem.addActionListener(e -> dispatcher.sendToAgentScan(items, "CtxMenu"));

        menu.add(ingestItem);
        menu.add(scanItem);
        menu.add(agentScanItem);

        return List.of(menu);
    }
}

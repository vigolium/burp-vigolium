package com.vigolium.extension;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vigolium.extension.config.VigoliumSettings;
import com.vigolium.extension.config.VigoliumSettingsImpl;
import com.vigolium.extension.controller.AgentSessionsController;
import com.vigolium.extension.controller.ContextMenuController;
import com.vigolium.extension.controller.FindingsController;
import com.vigolium.extension.controller.HotkeyController;
import com.vigolium.extension.controller.HttpRecordsController;
import com.vigolium.extension.controller.ProxyController;
import com.vigolium.extension.controller.ScansController;
import com.vigolium.extension.controller.SettingsController;
import com.vigolium.extension.filter.FilterEngine;
import com.vigolium.extension.filter.FilterRule;
import com.vigolium.extension.model.Finding;
import com.vigolium.extension.service.BurpBridgeService;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.RequestCounters;
import com.vigolium.extension.service.RequestDispatchService;
import com.vigolium.extension.service.SiteMapSnapshotService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.dialog.FilterRuleEditDialog;
import com.vigolium.extension.ui.panel.*;
import com.vigolium.extension.ui.tab.*;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class VigoliumExtension implements BurpExtension {

    private MontoyaApi montoyaApi;
    private FindingsController findingsController;
    private HttpRecordsController httpRecordsController;
    private ScansController scansController;
    private AgentSessionsController agentSessionsController;
    private SettingsController settingsController;
    private ProxyController proxyController;
    private HotkeyController hotkeyController;
    private RequestDispatchService dispatcher;
    private SiteMapSnapshotService snapshotService;
    private BurpBridgeService bridgeService;

    @Override
    public void initialize(MontoyaApi api) {
        this.montoyaApi = api;
        api.extension().setName("Vigolium");
        String extensionVersion = resolveExtensionVersion();

        // Services
        VigoliumSettings settings = new VigoliumSettingsImpl(api.persistence().preferences());
        LogService logService = new LogService();
        VigoliumApiService apiService = new VigoliumApiService(settings::getServerUrl, settings::getApiKey);
        FilterEngine filterEngine = new FilterEngine();
        snapshotService = new SiteMapSnapshotService(api, settings, apiService, logService);
        bridgeService = new BurpBridgeService(api, settings, logService);

        // UI Panels
        ServerConnectionPanel serverConnectionPanel = new ServerConnectionPanel();
        serverConnectionPanel.setServerUrl(settings.getServerUrl());
        serverConnectionPanel.setApiKey(settings.getApiKey());

        ScanOptionsPanel scanOptionsPanel = new ScanOptionsPanel();
        scanOptionsPanel.setCustomModules(settings.getCustomModules());
        scanOptionsPanel.setTimeout(settings.getScanTimeout());

        ProxyModePanel proxyModePanel = new ProxyModePanel();
        settings.setProxyEnabled(false);
        proxyModePanel.setInScopeOnly(settings.isInScopeOnly());

        FilterRulesPanel proxyFilterRulesPanel = new FilterRulesPanel();
        proxyFilterRulesPanel.setRules(settings.getProxyFilterRules());

        // Controllers
        settingsController = new SettingsController(settings, settings, settings, apiService, logService);

        settingsController.setOnConnectionSuccess(health -> {
            serverConnectionPanel.setConnectionStatus(true, "Connected (latency: " + health.latencyMs() + "ms)");
        });
        settingsController.setOnConnectionFailure(msg -> serverConnectionPanel.setConnectionStatus(false, msg));
        settingsController.setOnConnectionTesting(serverConnectionPanel::setTesting);

        // Wire server URL/API Key auto-save on field change
        serverConnectionPanel.addServerUrlDocumentListener(createAutoSaveListener(() -> {
            settings.setServerUrl(serverConnectionPanel.getServerUrl());
        }));
        serverConnectionPanel.addApiKeyDocumentListener(createAutoSaveListener(() -> {
            settings.setApiKey(serverConnectionPanel.getApiKey());
        }));

        // Scan options auto-save on field change
        scanOptionsPanel.addModulesDocumentListener(createAutoSaveListener(() -> {
            settings.setCustomModules(scanOptionsPanel.getCustomModules());
        }));
        scanOptionsPanel.addTimeoutDocumentListener(createAutoSaveListener(() -> {
            settings.setScanTimeout(scanOptionsPanel.getTimeout());
        }));

        // Scan All HTTP Records — confirm, then trigger /api/scan-all-records via SettingsController
        scanOptionsPanel.addScanAllListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(scanOptionsPanel),
                    "Scan all HTTP records in this project?\nThis can take a while and cannot be undone.",
                    "Scan All HTTP Records",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            scanOptionsPanel.setScanAllRunning(true);
            settingsController.scanAllRecords(
                    scanOptionsPanel.getCustomModules(),
                    scanOptionsPanel.getTimeout(),
                    scanUuid -> {
                        scanOptionsPanel.setScanAllRunning(false);
                        scanOptionsPanel.setScanAllResult(true, "Scan started: " + scanUuid);
                    },
                    msg -> {
                        scanOptionsPanel.setScanAllRunning(false);
                        scanOptionsPanel.setScanAllResult(false, "Failed: " + msg);
                    });
        });

        // Wire panel listeners
        serverConnectionPanel.addTestConnectionListener(e -> settingsController.testConnection());
        proxyModePanel.addToggleListener(e -> {
            boolean newState = !proxyModePanel.isProxyEnabled();
            proxyModePanel.setProxyEnabled(newState);
            settingsController.setProxyEnabled(newState);
        });
        proxyModePanel.addInScopeOnlyListener(e -> settings.setInScopeOnly(proxyModePanel.isInScopeOnly()));

        // Filter rule Add/Edit/Remove
        proxyFilterRulesPanel.addAddListener(e -> {
            Frame frame = (Frame) SwingUtilities.getWindowAncestor(proxyFilterRulesPanel);
            boolean isFirst = proxyFilterRulesPanel.getRules().isEmpty();
            FilterRuleEditDialog dialog = new FilterRuleEditDialog(frame, null, isFirst);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                List<FilterRule> rules = new ArrayList<>(proxyFilterRulesPanel.getRules());
                rules.add(dialog.getRule());
                proxyFilterRulesPanel.setRules(rules);
                settings.setProxyFilterRules(rules);
            }
        });

        proxyFilterRulesPanel.addEditListener(e -> {
            int idx = proxyFilterRulesPanel.getSelectedRuleIndex();
            if (idx < 0) return;
            Frame frame = (Frame) SwingUtilities.getWindowAncestor(proxyFilterRulesPanel);
            FilterRuleEditDialog dialog = new FilterRuleEditDialog(
                    frame, proxyFilterRulesPanel.getRules().get(idx), idx == 0);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                List<FilterRule> rules = new ArrayList<>(proxyFilterRulesPanel.getRules());
                rules.set(idx, dialog.getRule());
                proxyFilterRulesPanel.setRules(rules);
                settings.setProxyFilterRules(rules);
            }
        });

        proxyFilterRulesPanel.addRemoveListener(e -> {
            int idx = proxyFilterRulesPanel.getSelectedRuleIndex();
            if (idx < 0) return;
            List<FilterRule> rules = new ArrayList<>(proxyFilterRulesPanel.getRules());
            rules.remove(idx);
            proxyFilterRulesPanel.setRules(rules);
            settings.setProxyFilterRules(rules);
        });

        proxyFilterRulesPanel.addUpListener(e -> {
            int idx = proxyFilterRulesPanel.getSelectedRuleIndex();
            if (idx <= 0) return;
            List<FilterRule> rules = new ArrayList<>(proxyFilterRulesPanel.getRules());
            FilterRule rule = rules.remove(idx);
            rules.add(idx - 1, rule);
            proxyFilterRulesPanel.setRules(rules);
            proxyFilterRulesPanel.setSelectedRuleIndex(idx - 1);
            settings.setProxyFilterRules(rules);
        });

        proxyFilterRulesPanel.addDownListener(e -> {
            int idx = proxyFilterRulesPanel.getSelectedRuleIndex();
            List<FilterRule> rules = new ArrayList<>(proxyFilterRulesPanel.getRules());
            if (idx < 0 || idx >= rules.size() - 1) return;
            FilterRule rule = rules.remove(idx);
            rules.add(idx + 1, rule);
            proxyFilterRulesPanel.setRules(rules);
            proxyFilterRulesPanel.setSelectedRuleIndex(idx + 1);
            settings.setProxyFilterRules(rules);
        });

        proxyFilterRulesPanel.addResetDefaultListener(e -> {
            List<FilterRule> defaultRules = FilterRule.getDefaultRules();
            proxyFilterRulesPanel.setRules(defaultRules);
            settings.setProxyFilterRules(defaultRules);
        });

        // Request stats
        RequestStatsPanel requestStatsPanel = new RequestStatsPanel();

        RequestCounters ingestCounters = new RequestCounters();
        ingestCounters.setOnChanged(() -> requestStatsPanel
                .getIngestStats()
                .updateCounters(
                        ingestCounters.getSentCount(),
                        ingestCounters.getPendingCount(),
                        ingestCounters.getFailedCount()));

        RequestCounters scanCounters = new RequestCounters();
        scanCounters.setOnChanged(() -> requestStatsPanel
                .getScanStats()
                .updateCounters(
                        scanCounters.getSentCount(), scanCounters.getPendingCount(), scanCounters.getFailedCount()));

        // Request dispatch service (shared by context menu and hotkeys)
        dispatcher = new RequestDispatchService(apiService, logService, ingestCounters, scanCounters, settings);

        // Proxy Controller
        proxyController = new ProxyController(settings, settings, filterEngine, apiService, logService, ingestCounters);

        // Hotkey Controller
        hotkeyController = new HotkeyController(api, settings, dispatcher, () -> snapshotService.snapshotNow("Hotkey"));

        // Hotkeys Panel
        HotkeysPanel hotkeysPanel = new HotkeysPanel(hotkeyController);
        hotkeysPanel.setIngestHotkey(settings.getIngestHotkey());
        hotkeysPanel.setScanHotkey(settings.getScanHotkey());
        hotkeysPanel.setAgentScanHotkey(settings.getAgentScanHotkey());
        hotkeysPanel.setSnapshotSitemapHotkey(settings.getSnapshotSitemapHotkey());

        SiteMapSnapshotPanel siteMapSnapshotPanel = new SiteMapSnapshotPanel(settings, snapshotService);
        BridgePanel bridgePanel = new BridgePanel(settings, bridgeService);

        // Tabs
        FindingsTab findingsTab = new FindingsTab(api);
        HttpRecordsTab httpRecordsTab = new HttpRecordsTab(api);
        ScansTab scansTab = new ScansTab();
        AgentSessionsTab agentSessionsTab = new AgentSessionsTab();
        ScansContainerTab scansContainerTab = new ScansContainerTab(scansTab, agentSessionsTab);
        BridgeTab bridgeTab = new BridgeTab(siteMapSnapshotPanel, bridgePanel, proxyModePanel, proxyFilterRulesPanel);
        SettingsTab settingsTab =
                new SettingsTab(serverConnectionPanel, scanOptionsPanel, requestStatsPanel, hotkeysPanel);
        LogsTab logsTab = new LogsTab(logService);
        VigoliumTab mainTab =
                new VigoliumTab(findingsTab, httpRecordsTab, scansContainerTab, bridgeTab, settingsTab, logsTab);

        wireFindings(findingsTab, mainTab, apiService, logService);
        wireHttpRecords(httpRecordsTab, apiService, logService, settings);
        wireScans(scansTab, apiService, logService);
        wireAgentSessions(agentSessionsTab, apiService, logService);

        // Context Menu Controller
        ContextMenuController contextMenuController = new ContextMenuController(dispatcher);

        // Register with Burp
        api.userInterface().registerSuiteTab("Vigolium", mainTab);
        api.proxy().registerRequestHandler(proxyController);
        api.proxy().registerResponseHandler(proxyController);
        api.userInterface().registerContextMenuItemsProvider(contextMenuController);

        // Register hotkeys
        hotkeyController.register();
        snapshotService.start();
        bridgeService.start();

        // Extension unload handler
        api.extension().registerUnloadingHandler(() -> {
            findingsController.shutdown();
            httpRecordsController.shutdown();
            scansController.shutdown();
            agentSessionsController.shutdown();
            settingsController.shutdown();
            proxyController.shutdown();
            hotkeyController.shutdown();
            snapshotService.shutdown();
            bridgeService.shutdown();
            dispatcher.shutdown();
        });

        String loadedMessage = "[Vigolium] Extension v" + extensionVersion + " loaded successfully.";
        api.logging().logToOutput(loadedMessage);
        logService.addLog(LogService.Level.INFO, loadedMessage);
    }

    private static String resolveExtensionVersion() {
        String version = VigoliumExtension.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    private void wireFindings(
            FindingsTab findingsTab, VigoliumTab mainTab, VigoliumApiService apiService, LogService logService) {
        findingsController = new FindingsController(apiService, logService, findingsTab.getTableModel());

        findingsController.setOnPageLoaded(findingsTab::updatePagination);
        findingsController.setOnFindingSelected(findingsTab::showFinding);
        findingsTab.setOnFindingSelected(finding -> findingsController.fetchFindingDetail(finding));

        findingsTab
                .getSearchField()
                .addActionListener(e -> findingsController.setSearch(
                        findingsTab.getSearchField().getText().trim()));

        findingsTab.getSeverityCombo().addActionListener(e -> {
            String selected = (String) findingsTab.getSeverityCombo().getSelectedItem();
            findingsController.setSeverityFilter("All".equals(selected) ? null : selected.toLowerCase());
        });

        findingsTab.getModuleTypeCombo().addActionListener(e -> {
            String selected = (String) findingsTab.getModuleTypeCombo().getSelectedItem();
            findingsController.setModuleTypeFilter("Any type".equals(selected) ? null : selected);
        });

        findingsTab.getFindingSourceCombo().addActionListener(e -> {
            String selected = (String) findingsTab.getFindingSourceCombo().getSelectedItem();
            findingsController.setFindingSourceFilter("Any source".equals(selected) ? null : selected);
        });

        findingsTab
                .getScanIdField()
                .addActionListener(e -> findingsController.setScanIdFilter(
                        findingsTab.getScanIdField().getText().trim()));
        findingsTab
                .getRepoField()
                .addActionListener(e -> findingsController.setRepoFilter(
                        findingsTab.getRepoField().getText().trim()));
        findingsTab
                .getDomainField()
                .addActionListener(e -> findingsController.setDomainFilter(
                        findingsTab.getDomainField().getText().trim()));

        findingsTab.addPrevPageListener(e -> findingsController.prevPage());
        findingsTab.addNextPageListener(e -> findingsController.nextPage());
        findingsTab.addRefreshListener(e -> findingsController.refresh());

        findingsTab.getPageSizeCombo().addActionListener(e -> {
            String size = (String) findingsTab.getPageSizeCombo().getSelectedItem();
            if (size != null) findingsController.setPageSize(Integer.parseInt(size));
        });

        findingsTab.setOnSortChanged(findingsController::setSort);

        findingsTab.addExportJsonListener(e -> exportFindingsJson(findingsTab.getFindings(), mainTab));
        findingsTab.addCopyDetailsListener(e -> copyFindingToClipboard(findingsTab.getCurrentFinding(), logService));

        findingsController.fetchCurrentPage();
    }

    private void wireHttpRecords(
            HttpRecordsTab tab, VigoliumApiService apiService, LogService logService, VigoliumSettings settings) {
        httpRecordsController = new HttpRecordsController(apiService, logService, tab.getTableModel(), settings);
        httpRecordsController.setOnPageLoaded(tab::updatePagination);
        httpRecordsController.setOnRecordDetailLoaded(tab::setRecordDetail);

        tab.setOnRecordSelected(record -> httpRecordsController.fetchDetail(record));
        tab.setOnScanRecord(record -> httpRecordsController.scanRecord(record));
        tab.setOnDeleteRecord(record -> httpRecordsController.deleteRecord(record));
        tab.setOnSendToRepeater(record -> sendRecordToRepeater(record, logService));

        tab.getSearchField()
                .addActionListener(e -> httpRecordsController.setSearch(
                        tab.getSearchField().getText().trim()));
        tab.getDomainField()
                .addActionListener(e -> httpRecordsController.setDomain(
                        tab.getDomainField().getText().trim()));
        tab.getMethodCombo().addActionListener(e -> {
            String sel = (String) tab.getMethodCombo().getSelectedItem();
            httpRecordsController.setMethod("Any".equals(sel) ? null : sel);
        });
        tab.getStatusCombo().addActionListener(e -> {
            Object sel = tab.getStatusCombo().getSelectedItem();
            String value = sel == null ? null : sel.toString().trim();
            httpRecordsController.setStatusCode(
                    (value == null || value.isEmpty() || "Any".equals(value)) ? null : value);
        });
        tab.getContentTypeField()
                .addActionListener(e -> httpRecordsController.setContentType(
                        tab.getContentTypeField().getText().trim()));
        tab.getSourceField()
                .addActionListener(e -> httpRecordsController.setSource(
                        tab.getSourceField().getText().trim()));
        tab.getMinRiskField().addActionListener(e -> {
            String txt = tab.getMinRiskField().getText().trim();
            try {
                httpRecordsController.setMinRisk(txt.isEmpty() ? null : Integer.parseInt(txt));
            } catch (NumberFormatException ex) {
                httpRecordsController.setMinRisk(null);
            }
        });

        tab.addPrevPageListener(e -> httpRecordsController.prevPage());
        tab.addNextPageListener(e -> httpRecordsController.nextPage());
        tab.addRefreshListener(e -> httpRecordsController.refresh());
        tab.getPageSizeCombo().addActionListener(e -> {
            String size = (String) tab.getPageSizeCombo().getSelectedItem();
            if (size != null) httpRecordsController.setPageSize(Integer.parseInt(size));
        });
        tab.setOnSortChanged(httpRecordsController::setSort);

        httpRecordsController.fetchCurrentPage();
    }

    private void wireScans(ScansTab tab, VigoliumApiService apiService, LogService logService) {
        scansController = new ScansController(apiService, logService, tab.getTableModel());
        scansController.setOnPageLoaded(tab::updatePagination);
        scansController.setOnLogsLoaded(tab::setLogs);

        tab.addRefreshListener(e -> scansController.refresh());
        tab.addPrevPageListener(e -> scansController.prevPage());
        tab.addNextPageListener(e -> scansController.nextPage());
        tab.getPageSizeCombo().addActionListener(e -> {
            String size = (String) tab.getPageSizeCombo().getSelectedItem();
            if (size != null) scansController.setPageSize(Integer.parseInt(size));
        });

        tab.setOnScanSelected(scan -> {
            scansController.setLogFilter(tab.getLogLevel(), tab.getLogPhase());
            scansController.fetchLogs(scan.uuid());
        });
        tab.setOnPauseScan(scansController::pauseScan);
        tab.setOnResumeScan(scansController::resumeScan);
        tab.setOnStopScan(scansController::stopScan);
        tab.setOnDeleteScan(scansController::deleteScan);
        tab.setOnLogFilterChanged(() -> scansController.setLogFilter(tab.getLogLevel(), tab.getLogPhase()));

        tab.addLogFetchListener(e -> {
            com.vigolium.extension.model.Scan selected = tab.getSelectedScan();
            if (selected != null) {
                scansController.setLogFilter(tab.getLogLevel(), tab.getLogPhase());
                scansController.fetchLogs(selected.uuid());
            }
        });

        scansController.fetchCurrentPage();
    }

    private void wireAgentSessions(AgentSessionsTab tab, VigoliumApiService apiService, LogService logService) {
        agentSessionsController = new AgentSessionsController(apiService, logService, tab.getTableModel());
        agentSessionsController.setOnPageLoaded(tab::updatePagination);
        agentSessionsController.setOnLogsLoaded(tab::setLogText);

        tab.addRefreshListener(e -> agentSessionsController.refresh());
        tab.addPrevPageListener(e -> agentSessionsController.prevPage());
        tab.addNextPageListener(e -> agentSessionsController.nextPage());
        tab.getPageSizeCombo().addActionListener(e -> {
            String size = (String) tab.getPageSizeCombo().getSelectedItem();
            if (size != null) agentSessionsController.setPageSize(Integer.parseInt(size));
        });
        tab.getModeCombo().addActionListener(e -> agentSessionsController.setMode(tab.getSelectedMode()));

        tab.setOnSessionSelected(session -> agentSessionsController.fetchLogs(session.uuid()));

        tab.addLogFetchListener(e -> {
            com.vigolium.extension.model.AgentSession sel = tab.getSelectedSession();
            if (sel != null) agentSessionsController.fetchLogs(sel.uuid());
        });

        agentSessionsController.fetchCurrentPage();
    }

    private void sendRecordToRepeater(com.vigolium.extension.model.HttpRecord record, LogService logService) {
        if (record == null || record.rawRequest() == null || record.rawRequest().isEmpty()) {
            logService.addLog(LogService.Level.WARN, "[HTTP Records] No raw request available to send to Repeater");
            return;
        }
        try {
            burp.api.montoya.http.HttpService service = buildHttpService(record);
            burp.api.montoya.core.ByteArray ba = burp.api.montoya.core.ByteArray.byteArray(record.rawRequestBytes());
            burp.api.montoya.http.message.requests.HttpRequest req = service != null
                    ? burp.api.montoya.http.message.requests.HttpRequest.httpRequest(service, ba)
                    : burp.api.montoya.http.message.requests.HttpRequest.httpRequest(ba);
            montoyaApi.repeater().sendToRepeater(req, "vigolium-" + (record.uuid() != null ? record.uuid() : ""));
            logService.addLog(LogService.Level.INFO, "[HTTP Records] Sent to Repeater");
        } catch (Exception e) {
            logService.addLog(LogService.Level.WARN, "[HTTP Records] Send to Repeater failed: " + e.getMessage());
        }
    }

    private static burp.api.montoya.http.HttpService buildHttpService(com.vigolium.extension.model.HttpRecord record) {
        if (record.url() != null && !record.url().isEmpty()) {
            try {
                return burp.api.montoya.http.HttpService.httpService(record.url());
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (record.hostname() != null && !record.hostname().isEmpty()) {
            boolean secure = "https".equalsIgnoreCase(record.scheme());
            int port = record.port() > 0 ? record.port() : (secure ? 443 : 80);
            try {
                return burp.api.montoya.http.HttpService.httpService(record.hostname(), port, secure);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private void exportFindingsJson(List<Finding> findings, Component parent) {
        if (findings.isEmpty()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("vigolium-findings.json"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        try (PrintWriter writer = new PrintWriter(new FileWriter(chooser.getSelectedFile()))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray array = new JsonArray();
            for (Finding f : findings) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", f.id());
                obj.addProperty("severity", f.severity().name().toLowerCase());
                obj.addProperty("module_id", f.moduleId());
                obj.addProperty("module_name", f.moduleName());
                obj.addProperty("description", f.description());
                obj.addProperty("confidence", f.confidence());
                obj.addProperty("found_at", f.foundAt());
                if (f.matchedAt() != null && !f.matchedAt().isEmpty()) {
                    JsonArray matched = new JsonArray();
                    f.matchedAt().forEach(matched::add);
                    obj.add("matched_at", matched);
                }
                if (f.tags() != null && !f.tags().isEmpty()) {
                    JsonArray tags = new JsonArray();
                    f.tags().forEach(tags::add);
                    obj.add("tags", tags);
                }
                array.add(obj);
            }
            gson.toJson(array, writer);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    parent, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyFindingToClipboard(Finding f, LogService logService) {
        if (f == null) {
            logService.addLog(LogService.Level.WARN, "[Findings] No finding selected to copy");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(f.moduleName()).append("\n\n");
        sb.append("- **Severity:** ").append(f.severity().label()).append('\n');
        if (f.confidence() != null && !f.confidence().isEmpty()) {
            sb.append("- **Confidence:** ").append(f.confidence()).append('\n');
        }
        if (f.moduleType() != null && !f.moduleType().isEmpty()) {
            sb.append("- **Module type:** ").append(f.moduleType()).append('\n');
        }
        if (f.findingSource() != null && !f.findingSource().isEmpty()) {
            sb.append("- **Source:** ").append(f.findingSource()).append('\n');
        }
        if (f.moduleId() != null && !f.moduleId().isEmpty()) {
            sb.append("- **Module ID:** ").append(f.moduleId()).append('\n');
        }
        if (f.repoName() != null && !f.repoName().isEmpty()) {
            sb.append("- **Repo:** ").append(f.repoName()).append('\n');
        }
        if (f.sourceFile() != null && !f.sourceFile().isEmpty()) {
            sb.append("- **File:** ").append(f.sourceFile()).append('\n');
        }
        if (f.scanUuid() != null && !f.scanUuid().isEmpty()) {
            sb.append("- **Scan:** ").append(f.scanUuid()).append('\n');
        }
        if (f.foundAt() != null && !f.foundAt().isEmpty()) {
            sb.append("- **Found at:** ").append(f.foundAt()).append('\n');
        }
        if (f.tags() != null && !f.tags().isEmpty()) {
            sb.append("- **Tags:** ").append(String.join(", ", f.tags())).append('\n');
        }
        sb.append('\n');

        if (f.description() != null && !f.description().isEmpty()) {
            sb.append("## Description\n\n").append(f.description()).append("\n\n");
        }
        if (f.moduleShort() != null && !f.moduleShort().isEmpty()) {
            sb.append("_").append(f.moduleShort()).append("_\n\n");
        }
        if (f.matchedAt() != null && !f.matchedAt().isEmpty()) {
            sb.append("## Matched at\n\n");
            for (String url : f.matchedAt()) sb.append("- ").append(url).append('\n');
            sb.append('\n');
        }
        if (f.extractedResults() != null && !f.extractedResults().isEmpty()) {
            sb.append("## Extracted results\n\n```\n");
            for (String r : f.extractedResults()) sb.append(r).append('\n');
            sb.append("```\n\n");
        }
        if (f.request() != null && !f.request().isEmpty()) {
            sb.append("## Request\n\n```http\n").append(f.request()).append("\n```\n\n");
        }
        if (f.response() != null && !f.response().isEmpty()) {
            sb.append("## Response\n\n```http\n").append(f.response()).append("\n```\n\n");
        }
        for (int i = 0; i < f.additionalEvidence().size(); i++) {
            Finding.Evidence ev = Finding.parseEvidence(f.additionalEvidence().get(i));
            sb.append("## Evidence #").append(i + 1).append("\n\n");
            if (!ev.request().isEmpty()) {
                sb.append("### Request\n\n```http\n").append(ev.request()).append("\n```\n\n");
            }
            if (!ev.response().isEmpty()) {
                sb.append("### Response\n\n```http\n").append(ev.response()).append("\n```\n\n");
            }
        }

        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
            logService.addLog(LogService.Level.INFO, "[Findings] Finding copied to clipboard as Markdown");
        } catch (Exception ex) {
            logService.addLog(LogService.Level.WARN, "[Findings] Clipboard copy failed: " + ex.getMessage());
        }
    }

    private static DocumentListener createAutoSaveListener(Runnable saveAction) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                saveAction.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                saveAction.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                saveAction.run();
            }
        };
    }
}

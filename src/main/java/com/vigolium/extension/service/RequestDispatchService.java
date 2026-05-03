package com.vigolium.extension.service;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.vigolium.extension.config.ScanSettings;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RequestDispatchService {

    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private final VigoliumApiService apiService;
    private final LogService logService;
    private final RequestCounters ingestCounters;
    private final RequestCounters scanCounters;
    private final ScanSettings scanSettings;

    private final ExecutorService ingestExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-dispatch-ingest");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-dispatch-scan");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService agentScanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-dispatch-agent-scan");
        t.setDaemon(true);
        return t;
    });

    public RequestDispatchService(
            VigoliumApiService apiService,
            LogService logService,
            RequestCounters ingestCounters,
            RequestCounters scanCounters,
            ScanSettings scanSettings) {
        this.apiService = apiService;
        this.logService = logService;
        this.ingestCounters = ingestCounters;
        this.scanCounters = scanCounters;
        this.scanSettings = scanSettings;
    }

    public void sendToIngestion(List<HttpRequestResponse> items, String source) {
        if (items == null || items.isEmpty()) return;

        // Extract data synchronously while request/response objects are still valid
        List<IngestRequest> requests = new ArrayList<>(items.size());
        for (HttpRequestResponse rr : items) {
            requests.add(IngestRequest.fromRequestResponse(rr));
            ingestCounters.incrementPending();
        }

        CompletableFuture.runAsync(
                () -> {
                    int sent = 0;
                    for (IngestRequest request : requests) {
                        try {
                            apiService.ingest(request);
                            ingestCounters.markSent();
                            sent++;
                        } catch (Exception e) {
                            ingestCounters.markFailed();
                            logService.addLog(
                                    LogService.Level.ERROR,
                                    "[" + source + ":Ingest] Failed to send request: " + e.getMessage());
                        }
                    }
                    logService.addLog(
                            LogService.Level.INFO,
                            "[" + source + ":Ingest] Sent " + sent + "/" + requests.size() + " requests");
                },
                ingestExecutor);
    }

    public void sendToScan(List<HttpRequestResponse> items, String source) {
        if (items == null || items.isEmpty()) return;

        // Extract data synchronously while request/response objects are still valid
        String modules = emptyToNull(scanSettings.getCustomModules());
        String timeout = emptyToNull(scanSettings.getScanTimeout());
        List<ScanRequest> requests = new ArrayList<>(items.size());
        for (HttpRequestResponse rr : items) {
            String httpRequestBase64 =
                    BASE64.encodeToString(rr.request().toByteArray().getBytes());
            String httpResponseBase64 = rr.hasResponse()
                    ? BASE64.encodeToString(rr.response().toByteArray().getBytes())
                    : null;
            requests.add(new ScanRequest(
                    httpRequestBase64, httpResponseBase64, rr.request().url(), modules, timeout));
            scanCounters.incrementPending();
        }

        CompletableFuture.runAsync(
                () -> {
                    int sent = 0;
                    for (ScanRequest scanRequest : requests) {
                        try {
                            ScanResponse response = apiService.scan(scanRequest);
                            scanCounters.markSent();
                            sent++;
                            logService.addLog(
                                    LogService.Level.INFO,
                                    "[" + source + ":Scan] " + response.message() + " (scan_id: " + response.scanId()
                                            + ")");
                        } catch (VigoliumApiException e) {
                            scanCounters.markFailed();
                            if (e.statusCode() == 409) {
                                logService.addLog(
                                        LogService.Level.WARN, "[" + source + ":Scan] A scan is already running");
                            } else {
                                logService.addLog(
                                        LogService.Level.ERROR, "[" + source + ":Scan] Scan failed: " + e.getMessage());
                            }
                        } catch (Exception e) {
                            scanCounters.markFailed();
                            logService.addLog(
                                    LogService.Level.ERROR, "[" + source + ":Scan] Scan failed: " + e.getMessage());
                        }
                    }
                    logService.addLog(
                            LogService.Level.INFO,
                            "[" + source + ":Scan] Sent " + sent + "/" + requests.size() + " requests");
                },
                scanExecutor);
    }

    public void sendToAgentScan(List<HttpRequestResponse> items, String source) {
        if (items == null || items.isEmpty()) return;

        // Extract data synchronously while request/response objects are still valid
        List<AgentScanRequest> requests = new ArrayList<>(items.size());
        for (HttpRequestResponse rr : items) {
            String httpRequestBase64 =
                    BASE64.encodeToString(rr.request().toByteArray().getBytes());
            String httpResponseBase64 = rr.hasResponse()
                    ? BASE64.encodeToString(rr.response().toByteArray().getBytes())
                    : null;
            requests.add(new AgentScanRequest(
                    httpRequestBase64, httpResponseBase64, rr.request().url(), true, "balanced"));
        }

        CompletableFuture.runAsync(
                () -> {
                    int sent = 0;
                    for (AgentScanRequest agentScanRequest : requests) {
                        try {
                            String response = apiService.agentScan(agentScanRequest);
                            sent++;
                            logService.addLog(LogService.Level.INFO, "[" + source + ":AgentScan] Started: " + response);
                        } catch (Exception e) {
                            logService.addLog(
                                    LogService.Level.ERROR,
                                    "[" + source + ":AgentScan] Agent scan failed: " + e.getMessage());
                        }
                    }
                    logService.addLog(
                            LogService.Level.INFO,
                            "[" + source + ":AgentScan] Sent " + sent + "/" + requests.size() + " requests");
                },
                agentScanExecutor);
    }

    public static List<HttpRequestResponse> collectTargets(
            List<HttpRequestResponse> selected, Optional<? extends MessageEditorHttpRequestResponse> editorContext) {
        List<HttpRequestResponse> targets = new ArrayList<>();
        if (selected != null && !selected.isEmpty()) {
            targets.addAll(selected);
        }
        editorContext.ifPresent(editor -> {
            HttpRequestResponse rr = editor.requestResponse();
            if (rr != null && rr.request() != null) {
                targets.add(rr);
            }
        });
        return targets;
    }

    public void shutdown() {
        ingestExecutor.shutdownNow();
        scanExecutor.shutdownNow();
        agentScanExecutor.shutdownNow();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

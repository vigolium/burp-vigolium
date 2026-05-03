package com.vigolium.extension.controller;

import com.vigolium.extension.config.FilterSettings;
import com.vigolium.extension.config.ProxySettings;
import com.vigolium.extension.config.ServerSettings;
import com.vigolium.extension.service.HealthResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class SettingsController {

    private final ServerSettings serverSettings;
    private final ProxySettings proxySettings;
    private final FilterSettings filterSettings;
    private final VigoliumApiService apiService;
    private final LogService logService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-settings");
        t.setDaemon(true);
        return t;
    });

    private Consumer<HealthResponse> onConnectionSuccess;
    private Consumer<String> onConnectionFailure;
    private Consumer<Boolean> onConnectionTesting;

    public SettingsController(
            ServerSettings serverSettings,
            ProxySettings proxySettings,
            FilterSettings filterSettings,
            VigoliumApiService apiService,
            LogService logService) {
        this.serverSettings = serverSettings;
        this.proxySettings = proxySettings;
        this.filterSettings = filterSettings;
        this.apiService = apiService;
        this.logService = logService;
    }

    public void setOnConnectionSuccess(Consumer<HealthResponse> callback) {
        this.onConnectionSuccess = callback;
    }

    public void setOnConnectionFailure(Consumer<String> callback) {
        this.onConnectionFailure = callback;
    }

    public void setOnConnectionTesting(Consumer<Boolean> callback) {
        this.onConnectionTesting = callback;
    }

    public void testConnection() {
        if (onConnectionTesting != null) {
            SwingUtilities.invokeLater(() -> onConnectionTesting.accept(true));
        }

        CompletableFuture.supplyAsync(() -> apiService.health(), executor)
                .thenAcceptAsync(
                        response -> {
                            logService.addLog(
                                    LogService.Level.INFO,
                                    "Connected to Vigolium server (version: " + response.version() + ", latency: "
                                            + response.latencyMs() + "ms)");
                            if (onConnectionSuccess != null) {
                                onConnectionSuccess.accept(response);
                            }
                            if (onConnectionTesting != null) {
                                onConnectionTesting.accept(false);
                            }
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(ex -> {
                    String msg = extractMessage(ex);
                    logService.addLog(LogService.Level.ERROR, "Connection failed: " + msg);
                    SwingUtilities.invokeLater(() -> {
                        if (onConnectionFailure != null) {
                            onConnectionFailure.accept(msg);
                        }
                        if (onConnectionTesting != null) {
                            onConnectionTesting.accept(false);
                        }
                    });
                    return null;
                });
    }

    public void saveServerUrl(String url) {
        serverSettings.setServerUrl(url);
    }

    public void saveApiKey(String apiKey) {
        serverSettings.setApiKey(apiKey);
    }

    public void setProxyEnabled(boolean enabled) {
        proxySettings.setProxyEnabled(enabled);
        logService.addLog(LogService.Level.INFO, "Proxy mode " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Triggers POST /api/scan-all-records on the background executor. {@code modulesCsv} is the comma-separated module
     * list from the Scan Options field; {@code timeout} is the Go-duration string. Both pass through as-is when
     * non-blank, otherwise the server defaults apply (run all modules, default timeout). Callbacks fire on the EDT.
     */
    public void scanAllRecords(
            String modulesCsv, String timeout, Consumer<String> onSuccess, Consumer<String> onFailure) {
        List<String> modules = parseModulesCsv(modulesCsv);
        String timeoutArg = (timeout == null || timeout.isBlank()) ? null : timeout.trim();

        CompletableFuture.supplyAsync(() -> apiService.scanAllRecords(modules, timeoutArg), executor)
                .thenAcceptAsync(
                        scanUuid -> {
                            logService.addLog(LogService.Level.INFO, "[Scan All] Scan started: " + scanUuid);
                            if (onSuccess != null) onSuccess.accept(scanUuid);
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(ex -> {
                    String msg = extractMessage(ex);
                    logService.addLog(LogService.Level.ERROR, "[Scan All] Failed: " + msg);
                    SwingUtilities.invokeLater(() -> {
                        if (onFailure != null) onFailure.accept(msg);
                    });
                    return null;
                });
    }

    private static List<String> parseModulesCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String extractMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null
                ? cause.getMessage()
                : cause.getClass().getSimpleName();
    }
}

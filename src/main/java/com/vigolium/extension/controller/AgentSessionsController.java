package com.vigolium.extension.controller;

import com.vigolium.extension.service.AgentSessionsResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.AgentSessionsTableModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class AgentSessionsController {

    private final VigoliumApiService apiService;
    private final LogService logService;
    private final AgentSessionsTableModel tableModel;

    private int limit = 50;
    private int offset = 0;
    private String modeFilter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-agent-sessions-fetch");
        t.setDaemon(true);
        return t;
    });

    private Consumer<AgentSessionsResponse> onPageLoaded;
    private Consumer<String> onLogsLoaded;

    public AgentSessionsController(
            VigoliumApiService apiService, LogService logService, AgentSessionsTableModel tableModel) {
        this.apiService = apiService;
        this.logService = logService;
        this.tableModel = tableModel;
    }

    public void setOnPageLoaded(Consumer<AgentSessionsResponse> onPageLoaded) {
        this.onPageLoaded = onPageLoaded;
    }

    public void setOnLogsLoaded(Consumer<String> onLogsLoaded) {
        this.onLogsLoaded = onLogsLoaded;
    }

    public void fetchCurrentPage() {
        executor.submit(() -> {
            try {
                if (!apiService.isConfigured()) return;
                AgentSessionsResponse response = apiService.agentSessions(modeFilter, limit, offset);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setOffset(response.offset());
                    tableModel.setRows(response.data());
                    if (onPageLoaded != null) onPageLoaded.accept(response);
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Agent Sessions] Fetch failed: " + e.getMessage());
            }
        });
    }

    public void fetchLogs(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        executor.submit(() -> {
            try {
                String text = apiService.agentSessionLogs(sessionId);
                SwingUtilities.invokeLater(() -> {
                    if (onLogsLoaded != null) onLogsLoaded.accept(text);
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Agent Sessions] Log fetch failed: " + e.getMessage());
            }
        });
    }

    public void setMode(String mode) {
        this.modeFilter = mode;
        this.offset = 0;
        fetchCurrentPage();
    }

    public void setPageSize(int limit) {
        this.limit = limit;
        this.offset = 0;
        fetchCurrentPage();
    }

    public void nextPage() {
        this.offset += limit;
        fetchCurrentPage();
    }

    public void prevPage() {
        this.offset = Math.max(0, offset - limit);
        fetchCurrentPage();
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public void refresh() {
        fetchCurrentPage();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

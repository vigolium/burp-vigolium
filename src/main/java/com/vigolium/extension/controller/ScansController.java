package com.vigolium.extension.controller;

import com.vigolium.extension.model.Scan;
import com.vigolium.extension.model.ScanLogEntry;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.ScanLogsResponse;
import com.vigolium.extension.service.ScansResponse;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.ScansTableModel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class ScansController {

    private final VigoliumApiService apiService;
    private final LogService logService;
    private final ScansTableModel tableModel;

    private int limit = 50;
    private int offset = 0;

    private String logLevel;
    private String logPhase;
    private int logLimit = 500;
    private int logOffset = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-scans-fetch");
        t.setDaemon(true);
        return t;
    });

    private Consumer<ScansResponse> onPageLoaded;
    private Consumer<List<ScanLogEntry>> onLogsLoaded;

    public ScansController(VigoliumApiService apiService, LogService logService, ScansTableModel tableModel) {
        this.apiService = apiService;
        this.logService = logService;
        this.tableModel = tableModel;
    }

    public void setOnPageLoaded(Consumer<ScansResponse> onPageLoaded) {
        this.onPageLoaded = onPageLoaded;
    }

    public void setOnLogsLoaded(Consumer<List<ScanLogEntry>> onLogsLoaded) {
        this.onLogsLoaded = onLogsLoaded;
    }

    public void fetchCurrentPage() {
        executor.submit(() -> {
            try {
                if (!apiService.isConfigured()) return;
                ScansResponse response = apiService.scans(limit, offset);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setOffset(response.offset());
                    tableModel.setRows(response.data());
                    if (onPageLoaded != null) onPageLoaded.accept(response);
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Fetch failed: " + e.getMessage());
            }
        });
    }

    public void fetchLogs(String scanUuid) {
        if (scanUuid == null || scanUuid.isEmpty()) return;
        final String level = logLevel;
        final String phase = logPhase;
        final int lim = logLimit;
        final int off = logOffset;
        executor.submit(() -> {
            try {
                ScanLogsResponse response = apiService.scanLogs(scanUuid, level, phase, lim, off);
                SwingUtilities.invokeLater(() -> {
                    if (onLogsLoaded != null) onLogsLoaded.accept(response.logs());
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Log fetch failed: " + e.getMessage());
            }
        });
    }

    public void pauseScan(Scan scan) {
        executor.submit(() -> {
            try {
                apiService.pauseScan(scan.uuid());
                logService.addLog(LogService.Level.INFO, "[Scans] Pause requested " + scan.uuid());
                fetchCurrentPage();
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Pause failed: " + e.getMessage());
            }
        });
    }

    public void resumeScan(Scan scan) {
        executor.submit(() -> {
            try {
                apiService.resumeScan(scan.uuid());
                logService.addLog(LogService.Level.INFO, "[Scans] Resume requested " + scan.uuid());
                fetchCurrentPage();
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Resume failed: " + e.getMessage());
            }
        });
    }

    public void stopScan(Scan scan) {
        executor.submit(() -> {
            try {
                apiService.stopScan(scan.uuid());
                logService.addLog(LogService.Level.INFO, "[Scans] Stop requested " + scan.uuid());
                fetchCurrentPage();
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Stop failed: " + e.getMessage());
            }
        });
    }

    public void deleteScan(Scan scan) {
        executor.submit(() -> {
            try {
                apiService.deleteScan(scan.uuid());
                logService.addLog(LogService.Level.INFO, "[Scans] Deleted " + scan.uuid());
                fetchCurrentPage();
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Scans] Delete failed: " + e.getMessage());
            }
        });
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

    public void setLogFilter(String level, String phase) {
        this.logLevel = level;
        this.logPhase = phase;
        this.logOffset = 0;
    }

    public void setLogLimit(int limit) {
        this.logLimit = limit;
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

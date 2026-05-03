package com.vigolium.extension.controller;

import com.vigolium.extension.config.ScanSettings;
import com.vigolium.extension.model.HttpRecord;
import com.vigolium.extension.service.HttpRecordsQuery;
import com.vigolium.extension.service.HttpRecordsResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.HttpRecordsTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class HttpRecordsController {

    private final VigoliumApiService apiService;
    private final LogService logService;
    private final HttpRecordsTableModel tableModel;
    private final ScanSettings scanSettings;
    private final HttpRecordsQuery query = new HttpRecordsQuery();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-http-records-fetch");
        t.setDaemon(true);
        return t;
    });

    private Consumer<HttpRecordsResponse> onPageLoaded;
    private Consumer<HttpRecord> onRecordDetailLoaded;

    public HttpRecordsController(
            VigoliumApiService apiService,
            LogService logService,
            HttpRecordsTableModel tableModel,
            ScanSettings scanSettings) {
        this.apiService = apiService;
        this.logService = logService;
        this.tableModel = tableModel;
        this.scanSettings = scanSettings;
    }

    public void setOnPageLoaded(Consumer<HttpRecordsResponse> onPageLoaded) {
        this.onPageLoaded = onPageLoaded;
    }

    public void setOnRecordDetailLoaded(Consumer<HttpRecord> onRecordDetailLoaded) {
        this.onRecordDetailLoaded = onRecordDetailLoaded;
    }

    public HttpRecordsQuery getQuery() {
        return query;
    }

    public void fetchCurrentPage() {
        executor.submit(() -> {
            try {
                if (!apiService.isConfigured()) return;
                HttpRecordsResponse response = apiService.httpRecords(query);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setOffset(response.offset());
                    tableModel.setRows(response.data());
                    if (onPageLoaded != null) {
                        onPageLoaded.accept(response);
                    }
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[HTTP Records] Fetch failed: " + e.getMessage());
            }
        });
    }

    public void fetchDetail(HttpRecord summary) {
        executor.submit(() -> {
            try {
                HttpRecord full = apiService.httpRecordByUuid(summary.uuid());
                SwingUtilities.invokeLater(() -> {
                    if (onRecordDetailLoaded != null) onRecordDetailLoaded.accept(full);
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[HTTP Records] Detail fetch failed: " + e.getMessage());
            }
        });
    }

    public void deleteRecord(HttpRecord record) {
        executor.submit(() -> {
            try {
                apiService.deleteHttpRecord(record.uuid());
                logService.addLog(LogService.Level.INFO, "[HTTP Records] Deleted " + record.uuid());
                fetchCurrentPage();
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[HTTP Records] Delete failed: " + e.getMessage());
            }
        });
    }

    public void scanRecord(HttpRecord record) {
        executor.submit(() -> {
            try {
                String scanId = apiService.scanRecords(List.of(record.uuid()), parseModules(scanSettings));
                logService.addLog(
                        LogService.Level.INFO, "[HTTP Records] Scan started " + scanId + " for " + record.uuid());
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[HTTP Records] Scan failed: " + e.getMessage());
            }
        });
    }

    private static List<String> parseModules(ScanSettings settings) {
        String csv = settings == null ? null : settings.getCustomModules();
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    public void nextPage() {
        query.setOffset(query.getOffset() + query.getLimit());
        fetchCurrentPage();
    }

    public void prevPage() {
        int newOffset = query.getOffset() - query.getLimit();
        query.setOffset(Math.max(0, newOffset));
        fetchCurrentPage();
    }

    public void setSearch(String search) {
        query.setSearch(search);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setDomain(String domain) {
        query.setDomain(domain);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setMethod(String method) {
        query.setMethod(method);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setStatusCode(String statusCode) {
        query.setStatusCode(statusCode);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setContentType(String contentType) {
        query.setContentType(contentType);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setSource(String source) {
        query.setSource(source);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setMinRisk(Integer minRisk) {
        query.setMinRisk(minRisk);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setSort(String field, String order) {
        query.setSort(field);
        query.setOrder(order);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setPageSize(int limit) {
        query.setLimit(limit);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void refresh() {
        fetchCurrentPage();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

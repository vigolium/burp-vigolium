package com.vigolium.extension.controller;

import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import com.vigolium.extension.service.FindingsQuery;
import com.vigolium.extension.service.FindingsResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.FindingsTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class FindingsController {

    private static final Comparator<Finding> SEVERITY_ASC =
            Comparator.comparing(Finding::severity, Severity.BY_ORDINAL);
    private static final Comparator<Finding> SEVERITY_DESC = SEVERITY_ASC.reversed();

    private final VigoliumApiService apiService;
    private final LogService logService;
    private final FindingsTableModel tableModel;
    private final FindingsQuery query = new FindingsQuery();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vigolium-findings-fetch");
        t.setDaemon(true);
        return t;
    });

    private Consumer<FindingsResponse> onPageLoaded;
    private Consumer<Finding> onFindingSelected;

    public FindingsController(VigoliumApiService apiService, LogService logService, FindingsTableModel tableModel) {
        this.apiService = apiService;
        this.logService = logService;
        this.tableModel = tableModel;
    }

    public void setOnPageLoaded(Consumer<FindingsResponse> onPageLoaded) {
        this.onPageLoaded = onPageLoaded;
    }

    public void setOnFindingSelected(Consumer<Finding> onFindingSelected) {
        this.onFindingSelected = onFindingSelected;
    }

    public void fetchFindingDetail(Finding finding) {
        executor.submit(() -> {
            try {
                Finding full = apiService.findingById(finding.id());
                SwingUtilities.invokeLater(() -> {
                    if (onFindingSelected != null) onFindingSelected.accept(full);
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Findings] Detail fetch failed: " + e.getMessage());
            }
        });
    }

    public FindingsQuery getQuery() {
        return query;
    }

    public void fetchCurrentPage() {
        // Snapshot sort fields before async to avoid race with EDT mutations
        String sortField = query.getSort();
        String sortOrder = query.getOrder();
        executor.submit(() -> {
            try {
                if (!apiService.isConfigured()) return;
                FindingsResponse response = apiService.findings(query);
                List<Finding> data = sortSeverityClientSide(response.data(), sortField, sortOrder);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setOffset(response.offset());
                    tableModel.setRows(data);
                    if (onPageLoaded != null) {
                        onPageLoaded.accept(response);
                    }
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Findings] Fetch failed: " + e.getMessage());
            }
        });
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

    public void firstPage() {
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setSearch(String search) {
        query.setSearch(search);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setSeverityFilter(String severity) {
        query.setSeverity(severity);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setDomainFilter(String domain) {
        query.setDomain(domain);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setModuleFilter(String moduleName) {
        query.setModuleName(moduleName);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setModuleTypeFilter(String moduleType) {
        query.setModuleType(moduleType);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setFindingSourceFilter(String source) {
        query.setFindingSource(source);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setScanIdFilter(String scanId) {
        query.setScanId(scanId);
        query.setOffset(0);
        fetchCurrentPage();
    }

    public void setRepoFilter(String repo) {
        query.setRepoName(repo);
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

    /** API sorts severity alphabetically; re-sort by domain ordinal */
    private static List<Finding> sortSeverityClientSide(List<Finding> data, String sortField, String sortOrder) {
        if (!"severity".equals(sortField)) return data;
        List<Finding> sorted = new ArrayList<>(data);
        sorted.sort("desc".equals(sortOrder) ? SEVERITY_DESC : SEVERITY_ASC);
        return sorted;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

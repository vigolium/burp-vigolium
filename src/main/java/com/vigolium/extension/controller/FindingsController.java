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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class FindingsController {

    private static final Comparator<String> TEXT_ASC = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    private static final Set<String> CLIENT_SORT_FIELDS = Set.of("severity", "description", "matched_at");
    private static final int CLIENT_SORT_BATCH_SIZE = 500;

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
        FindingsQuery requestQuery = query.copy();
        executor.submit(() -> {
            try {
                if (!apiService.isConfigured()) return;
                FindingsResponse response = CLIENT_SORT_FIELDS.contains(requestQuery.getSort())
                        ? fetchClientSortedPage(requestQuery)
                        : apiService.findings(requestQuery);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setOffset(response.offset());
                    tableModel.setRows(response.data());
                    if (onPageLoaded != null) {
                        onPageLoaded.accept(response);
                    }
                });
            } catch (Exception e) {
                logService.addLog(LogService.Level.WARN, "[Findings] Fetch failed: " + e.getMessage());
            }
        });
    }

    private FindingsResponse fetchClientSortedPage(FindingsQuery requestQuery) {
        FindingsQuery batchQuery = requestQuery.copy();
        batchQuery.setLimit(CLIENT_SORT_BATCH_SIZE);
        batchQuery.setOffset(0);
        batchQuery.setSort("found_at");
        batchQuery.setOrder("desc");

        LinkedHashMap<Integer, Finding> all = new LinkedHashMap<>();
        while (true) {
            FindingsResponse batch = apiService.findings(batchQuery);
            for (Finding finding : batch.data()) all.put(finding.id(), finding);
            if (!batch.hasMore() || batch.data().isEmpty()) break;
            int nextOffset = batchQuery.getOffset() + batch.data().size();
            if (nextOffset <= batchQuery.getOffset()) break;
            batchQuery.setOffset(nextOffset);
        }

        List<Finding> sorted = sortPage(new ArrayList<>(all.values()), requestQuery.getSort(), requestQuery.getOrder());
        int from = Math.min(requestQuery.getOffset(), sorted.size());
        int to = requestQuery.getLimit() == 0 ? sorted.size() : Math.min(sorted.size(), from + requestQuery.getLimit());
        return new FindingsResponse(
                new ArrayList<>(sorted.subList(from, to)),
                sorted.size(),
                requestQuery.getLimit(),
                from,
                to < sorted.size());
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

    /** Sorts a complete filtered result before the caller applies pagination. */
    static List<Finding> sortPage(List<Finding> data, String sortField, String sortOrder) {
        Comparator<Finding> comparator =
                switch (sortField) {
                    case "severity" -> Comparator.comparing(
                            Finding::severity, Comparator.nullsLast(Severity.BY_ORDINAL));
                    case "module_name" -> Comparator.comparing(Finding::moduleName, TEXT_ASC);
                    case "description" -> Comparator.comparing(Finding::description, TEXT_ASC);
                    case "confidence" -> Comparator.comparing(Finding::confidence, TEXT_ASC);
                    case "matched_at" -> Comparator.comparing(FindingsController::firstMatchedUrl, TEXT_ASC);
                    case "found_at" -> Comparator.comparing(Finding::foundAt, TEXT_ASC);
                    default -> null;
                };
        if (comparator == null) return data;
        List<Finding> sorted = new ArrayList<>(data);
        if ("desc".equals(sortOrder)) comparator = comparator.reversed();
        sorted.sort(comparator.thenComparingInt(Finding::id));
        return sorted;
    }

    private static String firstMatchedUrl(Finding finding) {
        return finding.matchedAt().isEmpty() ? "" : finding.matchedAt().get(0);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

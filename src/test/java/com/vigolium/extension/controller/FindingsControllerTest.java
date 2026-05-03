package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import com.vigolium.extension.service.FindingsQuery;
import com.vigolium.extension.service.FindingsResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.FindingsTableModel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindingsControllerTest {

    private VigoliumApiService apiService;
    private LogService logService;
    private FindingsTableModel tableModel;
    private FindingsController controller;

    @BeforeEach
    void setUp() {
        apiService = mock(VigoliumApiService.class);
        when(apiService.isConfigured()).thenReturn(true);
        logService = new LogService();
        tableModel = new FindingsTableModel();
        controller = new FindingsController(apiService, logService, tableModel);
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }

    @Test
    void fetchCurrentPage_callsApiWithQueryParams() throws Exception {
        Finding finding = createFinding(1, Severity.HIGH);
        FindingsResponse response = new FindingsResponse(List.of(finding), 1, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(response);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.fetchCurrentPage();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        verify(apiService).findings(any(FindingsQuery.class));
    }

    @Test
    void nextPage_incrementsOffset() throws Exception {
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 100, 50, 50, true);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.nextPage();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(50, controller.getQuery().getOffset());
    }

    @Test
    void prevPage_decrementsOffset() throws Exception {
        controller.getQuery().setOffset(50);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 100, 50, 0, true);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.prevPage();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
    }

    @Test
    void prevPage_doesNotGoNegative() throws Exception {
        controller.getQuery().setOffset(10);
        controller.getQuery().setLimit(50);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 100, 50, 0, true);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.prevPage();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
    }

    @Test
    void setSearch_resetsOffsetAndFetches() throws Exception {
        controller.getQuery().setOffset(100);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 0, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setSearch("reflected");
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
        assertEquals("reflected", controller.getQuery().getSearch());
    }

    @Test
    void setSeverityFilter_resetsOffsetAndFetches() throws Exception {
        controller.getQuery().setOffset(100);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 0, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setSeverityFilter("high");
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
        assertEquals("high", controller.getQuery().getSeverity());
    }

    @Test
    void setSort_resetsOffsetAndFetches() throws Exception {
        controller.getQuery().setOffset(100);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 0, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setSort("severity", "asc");
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
        assertEquals("severity", controller.getQuery().getSort());
        assertEquals("asc", controller.getQuery().getOrder());
    }

    @Test
    void sortBySeverity_sortsClientSideByOrdinal() throws Exception {
        Finding low = createFinding(1, Severity.LOW);
        Finding critical = createFinding(2, Severity.CRITICAL);
        Finding medium = createFinding(3, Severity.MEDIUM);
        Finding high = createFinding(4, Severity.HIGH);
        FindingsResponse response = new FindingsResponse(List.of(critical, high, low, medium), 4, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(response);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setSort("severity", "asc");
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(4, tableModel.getRowCount());
        assertEquals(Severity.CRITICAL, tableModel.getRow(0).severity());
        assertEquals(Severity.HIGH, tableModel.getRow(1).severity());
        assertEquals(Severity.MEDIUM, tableModel.getRow(2).severity());
        assertEquals(Severity.LOW, tableModel.getRow(3).severity());
    }

    @Test
    void sortBySeverityDesc_sortsReversed() throws Exception {
        Finding low = createFinding(1, Severity.LOW);
        Finding critical = createFinding(2, Severity.CRITICAL);
        Finding high = createFinding(3, Severity.HIGH);
        FindingsResponse response = new FindingsResponse(List.of(critical, high, low), 3, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(response);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setSort("severity", "desc");
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(3, tableModel.getRowCount());
        assertEquals(Severity.LOW, tableModel.getRow(0).severity());
        assertEquals(Severity.HIGH, tableModel.getRow(1).severity());
        assertEquals(Severity.CRITICAL, tableModel.getRow(2).severity());
    }

    @Test
    void fetchCurrentPage_handlesApiError() throws Exception {
        when(apiService.findings(any(FindingsQuery.class))).thenThrow(new RuntimeException("connection refused"));

        controller.fetchCurrentPage();
        Thread.sleep(500);

        assertEquals(0, tableModel.getRowCount());
        assertTrue(logService.getEntries().stream()
                .anyMatch(e -> e.level() == LogService.Level.WARN && e.message().contains("Fetch failed")));
    }

    @Test
    void setPageSize_resetsOffsetAndFetches() throws Exception {
        controller.getQuery().setOffset(100);
        FindingsResponse emptyResponse = new FindingsResponse(List.of(), 0, 25, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(emptyResponse);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> latch.countDown());
        controller.setPageSize(25);
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(0, controller.getQuery().getOffset());
        assertEquals(25, controller.getQuery().getLimit());
    }

    @Test
    void onPageLoaded_receivesResponse() throws Exception {
        Finding finding = createFinding(1, Severity.CRITICAL);
        FindingsResponse response = new FindingsResponse(List.of(finding), 42, 50, 0, false);
        when(apiService.findings(any(FindingsQuery.class))).thenReturn(response);

        AtomicReference<FindingsResponse> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnPageLoaded(r -> {
            received.set(r);
            latch.countDown();
        });
        controller.fetchCurrentPage();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(42, received.get().total());
        assertEquals(1, received.get().data().size());
    }

    private Finding createFinding(int id, Severity severity) {
        return new Finding(
                id,
                List.of(),
                "",
                "sqli",
                "SQL Injection Scanner",
                "SQL Injection found",
                severity,
                "firm",
                List.of(),
                List.of("https://example.com/api"),
                "2026-02-16T10:00:00Z",
                "",
                "");
    }
}

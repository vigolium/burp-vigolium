package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vigolium.extension.config.ScanSettings;
import com.vigolium.extension.model.HttpRecord;
import com.vigolium.extension.service.HttpRecordsQuery;
import com.vigolium.extension.service.HttpRecordsResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import com.vigolium.extension.ui.table.HttpRecordsTableModel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpRecordsControllerSortTest {

    @Test
    void sortPage_supportsEveryHttpRecordsDataField() {
        HttpRecord first = record("a", "GET", 200, "a.example", "/a", 10, 20, 1, "audit", "2026-01-01T00:00:00Z");
        HttpRecord second = record("b", "POST", 500, "b.example", "/b", 20, 30, 2, "proxy", "2026-02-01T00:00:00Z");
        List<String> fields = List.of(
                "uuid",
                "method",
                "status_code",
                "hostname",
                "path",
                "response_content_length",
                "response_time",
                "risk_score",
                "source",
                "sent_at",
                "created_at");

        for (String field : fields) {
            assertEquals(
                    "a",
                    HttpRecordsController.sortPage(List.of(second, first), field, "asc")
                            .get(0)
                            .uuid(),
                    field);
            assertEquals(
                    "b",
                    HttpRecordsController.sortPage(List.of(first, second), field, "desc")
                            .get(0)
                            .uuid(),
                    field);
        }
    }

    @Test
    void clientOnlySortsCompleteFilteredResultBeforePagination() throws Exception {
        VigoliumApiService apiService = mock(VigoliumApiService.class);
        when(apiService.isConfigured()).thenReturn(true);
        HttpRecord zulu = record("z", "GET", 200, "z.example", "/z", 10, 20, 1, "audit", "2026-03-01");
        HttpRecord yankee = record("y", "GET", 200, "y.example", "/y", 10, 20, 1, "audit", "2026-02-01");
        HttpRecord alpha = record("a", "GET", 200, "a.example", "/a", 10, 20, 1, "audit", "2026-01-01");
        when(apiService.httpRecords(any(HttpRecordsQuery.class))).thenAnswer(invocation -> {
            HttpRecordsQuery requested = invocation.getArgument(0);
            return requested.getOffset() == 0
                    ? new HttpRecordsResponse(List.of(zulu, yankee), 3, 2, 0, true)
                    : new HttpRecordsResponse(List.of(alpha), 3, 2, 2, false);
        });
        HttpRecordsTableModel tableModel = new HttpRecordsTableModel();
        HttpRecordsController controller =
                new HttpRecordsController(apiService, new LogService(), tableModel, mock(ScanSettings.class));
        try {
            controller.getQuery().setLimit(2);
            CountDownLatch latch = new CountDownLatch(1);
            controller.setOnPageLoaded(response -> latch.countDown());

            controller.setSort("hostname", "asc");

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals("a", tableModel.getRow(0).uuid());
            assertEquals("y", tableModel.getRow(1).uuid());
            verify(apiService, times(2)).httpRecords(any(HttpRecordsQuery.class));
        } finally {
            controller.shutdown();
        }
    }

    private static HttpRecord record(
            String uuid,
            String method,
            int status,
            String host,
            String path,
            int length,
            int time,
            int risk,
            String source,
            String timestamp) {
        return new HttpRecord(
                uuid,
                "https",
                host,
                443,
                method,
                path,
                "https://" + host + path,
                status,
                "",
                "HTTP/1.1",
                length,
                time,
                timestamp,
                timestamp,
                source,
                risk,
                "",
                "");
    }
}

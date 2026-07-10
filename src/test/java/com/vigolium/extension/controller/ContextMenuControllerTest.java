package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import com.vigolium.extension.config.ScanSettings;
import com.vigolium.extension.service.IngestRequest;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.RequestCounters;
import com.vigolium.extension.service.RequestDispatchService;
import com.vigolium.extension.service.ScanRequest;
import com.vigolium.extension.service.ScanResponse;
import com.vigolium.extension.service.VigoliumApiException;
import com.vigolium.extension.service.VigoliumApiService;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextMenuControllerTest {

    private VigoliumApiService apiService;
    private LogService logService;
    private RequestDispatchService dispatcher;
    private ContextMenuController controller;

    @BeforeEach
    void setUp() {
        apiService = mock(VigoliumApiService.class);
        logService = new LogService();
        ScanSettings scanSettings = mock(ScanSettings.class);
        when(scanSettings.getCustomModules()).thenReturn("");
        when(scanSettings.getScanTimeout()).thenReturn("");
        dispatcher = new RequestDispatchService(
                apiService, logService, new RequestCounters(), new RequestCounters(), scanSettings);
        controller = new ContextMenuController(dispatcher);
    }

    @AfterEach
    void tearDown() {
        dispatcher.shutdown();
    }

    // --- Menu structure ---

    @Test
    void provideMenuItems_withSelectedRequests_returnsDirectActions() {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        HttpRequestResponse rr = createMockRequestResponse("GET", "/api", "example.com");
        when(event.selectedRequestResponses()).thenReturn(List.of(rr));

        List<Component> items = controller.provideMenuItems(event);

        assertEquals(3, items.size());
        assertInstanceOf(JMenuItem.class, items.get(0));
        assertEquals("Send to Ingestion", ((JMenuItem) items.get(0)).getText());
        assertEquals("Send to Native Scan", ((JMenuItem) items.get(1)).getText());
        assertEquals("Send to Agentic Scan", ((JMenuItem) items.get(2)).getText());
    }

    @Test
    void provideMenuItems_noSelectedRequests_returnsEmpty() {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        when(event.selectedRequestResponses()).thenReturn(List.of());

        List<Component> items = controller.provideMenuItems(event);
        assertTrue(items.isEmpty());
    }

    @Test
    void provideMenuItems_nullSelectedRequests_returnsEmpty() {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        when(event.selectedRequestResponses()).thenReturn(null);

        List<Component> items = controller.provideMenuItems(event);
        assertTrue(items.isEmpty());
    }

    @Test
    void provideMenuItems_menuItemsHaveCorrectNames() {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        HttpRequestResponse rr = createMockRequestResponse("GET", "/api", "example.com");
        when(event.selectedRequestResponses()).thenReturn(List.of(rr));

        List<Component> items = controller.provideMenuItems(event);
        assertEquals("sendToIngestionMenuItem", items.get(0).getName());
        assertEquals("sendToScanMenuItem", items.get(1).getName());
        assertEquals("sendToAgentScanMenuItem", items.get(2).getName());
    }

    // --- sendToIngestion via dispatcher ---

    @Test
    void sendToIngestion_fullPipeline() throws Exception {
        List<HttpRequestResponse> items = List.of(
                createMockRequestResponse("GET", "/api/1", "example.com"),
                createMockRequestResponse("GET", "/api/2", "example.com"));

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(inv -> {
                    latch.countDown();
                    return null;
                })
                .when(apiService)
                .ingest(any());

        dispatcher.sendToIngestion(items, "CtxMenu");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        verify(apiService, times(2)).ingest(any(IngestRequest.class));
    }

    @Test
    void sendToIngestion_logsResults() throws Exception {
        List<HttpRequestResponse> items = List.of(createMockRequestResponse("GET", "/api/1", "example.com"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    return null;
                })
                .when(apiService)
                .ingest(any());

        dispatcher.sendToIngestion(items, "CtxMenu");
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertTrue(logService.getEntries().stream()
                .anyMatch(e ->
                        e.message().contains("[CtxMenu:Ingest]") && e.message().contains("Sent")));
    }

    @Test
    void sendToIngestion_apiFailure_logsError() throws Exception {
        doThrow(new RuntimeException("429 Too Many Requests")).when(apiService).ingest(any());

        List<HttpRequestResponse> items = List.of(createMockRequestResponse("GET", "/api/1", "example.com"));

        dispatcher.sendToIngestion(items, "CtxMenu");
        Thread.sleep(1000);

        assertTrue(logService.getEntries().stream()
                .anyMatch(e -> e.level() == LogService.Level.ERROR
                        && e.message().contains("[CtxMenu:Ingest]")
                        && e.message().contains("Failed")));
    }

    // --- sendToScan via dispatcher ---

    @Test
    void sendToScan_callsScanEndpoint() throws Exception {
        when(apiService.scan(any())).thenReturn(new ScanResponse("scan-123", "running", "scan-request started"));

        List<HttpRequestResponse> items = List.of(createMockRequestResponse("GET", "/api/1", "example.com"));

        dispatcher.sendToScan(items, "CtxMenu");
        Thread.sleep(1000);

        verify(apiService, times(1)).scan(any(ScanRequest.class));
    }

    @Test
    void sendToScan_multipleSelected_sendsAll() throws Exception {
        when(apiService.scan(any())).thenReturn(new ScanResponse("scan-123", "running", "scan-request started"));

        List<HttpRequestResponse> items = List.of(
                createMockRequestResponse("GET", "/api/1", "example.com"),
                createMockRequestResponse("POST", "/api/2", "example.com"));

        dispatcher.sendToScan(items, "CtxMenu");
        Thread.sleep(1000);

        verify(apiService, times(2)).scan(any(ScanRequest.class));
    }

    @Test
    void sendToScan_409_logsWarning() throws Exception {
        doThrow(new VigoliumApiException(409, "scan already running"))
                .when(apiService)
                .scan(any());

        List<HttpRequestResponse> items = List.of(createMockRequestResponse("GET", "/api/1", "example.com"));

        dispatcher.sendToScan(items, "CtxMenu");
        Thread.sleep(1000);

        assertTrue(logService.getEntries().stream()
                .anyMatch(
                        e -> e.level() == LogService.Level.WARN && e.message().contains("A scan is already running")));
    }

    @Test
    void sendToScan_otherError_logsError() throws Exception {
        doThrow(new VigoliumApiException(400, "missing raw_request"))
                .when(apiService)
                .scan(any());

        List<HttpRequestResponse> items = List.of(createMockRequestResponse("GET", "/api/1", "example.com"));

        dispatcher.sendToScan(items, "CtxMenu");
        Thread.sleep(1000);

        assertTrue(logService.getEntries().stream()
                .anyMatch(e -> e.level() == LogService.Level.ERROR
                        && e.message().contains("[CtxMenu:Scan]")
                        && e.message().contains("Scan failed")));
    }

    // --- Helper ---

    private HttpRequestResponse createMockRequestResponse(String method, String path, String host) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn(method);
        when(request.pathWithoutQuery()).thenReturn(path);
        when(request.headerValue("Host")).thenReturn(host);
        when(request.url()).thenReturn("https://" + host + path);
        when(request.hasParameters()).thenReturn(false);
        when(request.parameters()).thenReturn(List.of());
        byte[] reqBytes =
                (method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        ByteArray reqByteArray = mock(ByteArray.class);
        when(reqByteArray.getBytes()).thenReturn(reqBytes);
        when(request.toByteArray()).thenReturn(reqByteArray);

        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) 200);
        when(response.headerValue("Content-Type")).thenReturn("application/json");
        byte[] respBytes = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        ByteArray respByteArray = mock(ByteArray.class);
        when(respByteArray.getBytes()).thenReturn(respBytes);
        when(response.toByteArray()).thenReturn(respByteArray);

        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        when(rr.request()).thenReturn(request);
        when(rr.response()).thenReturn(response);

        return rr;
    }
}

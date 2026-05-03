package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.vigolium.extension.config.FilterSettings;
import com.vigolium.extension.config.ProxySettings;
import com.vigolium.extension.filter.FilterEngine;
import com.vigolium.extension.filter.FilterRule;
import com.vigolium.extension.filter.MatchType;
import com.vigolium.extension.filter.Relationship;
import com.vigolium.extension.service.IngestRequest;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.RequestCounters;
import com.vigolium.extension.service.VigoliumApiService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyControllerTest {

    private ProxySettings proxySettings;
    private FilterSettings filterSettings;
    private FilterEngine filterEngine;
    private VigoliumApiService apiService;
    private LogService logService;
    private RequestCounters counters;
    private ProxyController controller;

    @BeforeEach
    void setUp() {
        proxySettings = mock(ProxySettings.class);
        filterSettings = mock(FilterSettings.class);
        filterEngine = new FilterEngine();
        apiService = mock(VigoliumApiService.class);
        logService = new LogService();
        counters = new RequestCounters();

        when(proxySettings.isProxyEnabled()).thenReturn(true);
        when(apiService.isConfigured()).thenReturn(true);
        when(filterSettings.getProxyFilterRules()).thenReturn(List.of());

        controller = new ProxyController(proxySettings, filterSettings, filterEngine, apiService, logService, counters);
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }

    // --- Full pipeline: proxy ON → filter pass → API call ---

    @Test
    void fullPipeline_proxyOnFilterPass_sendsToApi() throws Exception {
        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/users", "example.com");

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    return null;
                })
                .when(apiService)
                .ingest(any());

        controller.processRequestResponse(rr);

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        verify(apiService).ingest(any(IngestRequest.class));
    }

    @Test
    void fullPipeline_successIncrementsSentCounter() throws Exception {
        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/users", "example.com");

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    return null;
                })
                .when(apiService)
                .ingest(any());

        controller.processRequestResponse(rr);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertEquals(1, counters.getSentCount());
        assertEquals(0, counters.getPendingCount());
        assertEquals(0, counters.getFailedCount());
    }

    // --- Proxy OFF skips ---

    @Test
    void processRequest_proxyOff_doesNotProcess() throws Exception {
        when(proxySettings.isProxyEnabled()).thenReturn(false);

        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/test", "example.com");

        Thread.sleep(300);
        verify(apiService, never()).ingest(any());
    }

    // --- Filter rejects ---

    @Test
    void processRequest_filterRejects_doesNotSendToApi() throws Exception {
        FilterRule rule = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "POST");
        when(filterSettings.getProxyFilterRules()).thenReturn(List.of(rule));

        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/users", "example.com");

        controller.processRequestResponse(rr);
        Thread.sleep(500);

        verify(apiService, never()).ingest(any());
        assertEquals(0, counters.getSentCount());
    }

    // --- Failure increments counter ---

    @Test
    void processRequest_apiFailure_incrementsFailedCounter() throws Exception {
        HttpRequestResponse rr = createMockRequestResponse("GET", "/fail", "example.com");

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    throw new RuntimeException("timeout");
                })
                .when(apiService)
                .ingest(any());

        controller.processRequestResponse(rr);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(1, counters.getFailedCount());
        assertEquals(0, counters.getSentCount());
        assertEquals(0, counters.getPendingCount());
    }

    @Test
    void processRequest_apiFailure_logsError() throws Exception {
        HttpRequestResponse rr = createMockRequestResponse("GET", "/fail", "example.com");

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    throw new RuntimeException("server error");
                })
                .when(apiService)
                .ingest(any());

        controller.processRequestResponse(rr);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertTrue(logService.getEntries().stream()
                .anyMatch(e -> e.level() == LogService.Level.ERROR
                        && e.message().contains("[Proxy]")
                        && e.message().contains("server error")));
    }

    // --- Background execution ---

    @Test
    void processRequest_executesInBackground() throws Exception {
        CountDownLatch apiLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);

        doAnswer(inv -> {
                    apiLatch.countDown();
                    blockLatch.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(apiService)
                .ingest(any());

        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/test", "example.com");

        long start = System.currentTimeMillis();
        controller.processRequestResponse(rr);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 500, "Should not block: took " + elapsed + "ms");

        assertTrue(apiLatch.await(3, TimeUnit.SECONDS));
        blockLatch.countDown();
    }

    // --- Pending counter tracks in-flight requests ---

    @Test
    void processRequest_pendingCountTracked() throws Exception {
        CountDownLatch apiLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);

        doAnswer(inv -> {
                    apiLatch.countDown();
                    blockLatch.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(apiService)
                .ingest(any());

        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/pending", "example.com");
        controller.processRequestResponse(rr);

        assertTrue(apiLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, counters.getPendingCount());

        blockLatch.countDown();
        Thread.sleep(200);
        assertEquals(0, counters.getPendingCount());
        assertEquals(1, counters.getSentCount());
    }

    // --- Counters changed callback fires on EDT ---

    @Test
    void countersChangedCallbackFires() throws Exception {
        AtomicInteger callbackCount = new AtomicInteger();
        counters.setOnChanged(callbackCount::incrementAndGet);

        HttpRequestResponse rr = createMockRequestResponse("GET", "/api/cb", "example.com");
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
                    latch.countDown();
                    return null;
                })
                .when(apiService)
                .ingest(any());

        controller.processRequestResponse(rr);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(500);

        // At least 2: one for pending++, one for completion
        assertTrue(callbackCount.get() >= 2);
    }

    // --- Helper ---

    private HttpRequestResponse createMockRequestResponse(String method, String path, String host) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn(method);
        when(request.pathWithoutQuery()).thenReturn(path);
        when(request.headerValue("Host")).thenReturn(host);
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

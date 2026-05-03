package com.vigolium.extension.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vigolium.extension.model.Severity;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VigoliumApiServiceTest {

    private MockWebServer server;
    private VigoliumApiService service;
    private OkHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build();

        service = new VigoliumApiService(() -> server.url("/").toString(), () -> "test-api-key", client);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ──── Health endpoint ────

    @Test
    void health_callsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.2.3\"}")
                .setHeader("Content-Type", "application/json"));

        service.health();

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/health", request.getPath());
    }

    @Test
    void health_sendsBearerToken() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        service.health();

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }

    @Test
    void health_parsesStatusAndVersion() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"2.5.1\"}")
                .setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();

        assertEquals("ok", response.status());
        assertEquals("2.5.1", response.version());
        assertTrue(response.latencyMs() >= 0);
    }

    @Test
    void health_handlesUnknownFields() throws Exception {
        server.enqueue(new MockResponse().setBody("{}").setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();

        assertEquals("unknown", response.status());
        assertEquals("unknown", response.version());
    }

    // ──── Ingest endpoint ────

    @Test
    void ingest_callsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        service.ingest(new IngestRequest("burp_base64", "https://example.com", "cmVx", "cmVzcA=="));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/ingest-http", request.getPath());
    }

    @Test
    void ingest_sendsBearerToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        service.ingest(new IngestRequest("burp_base64", "https://example.com", "cmVx", "cmVzcA=="));

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }

    @Test
    void ingest_sendsCorrectJsonFormat() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        String rawReq = "GET / HTTP/1.1\r\nHost: test\r\n\r\n";
        String rawResp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html></html>";
        String reqB64 = Base64.getEncoder().encodeToString(rawReq.getBytes());
        String respB64 = Base64.getEncoder().encodeToString(rawResp.getBytes());

        service.ingest(new IngestRequest("burp_base64", "https://test", reqB64, respB64));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("burp_base64", json.get("input_mode").getAsString());
        assertEquals(reqB64, json.get("http_request_base64").getAsString());
        assertEquals(respB64, json.get("http_response_base64").getAsString());
    }

    @Test
    void ingest_sendsApplicationJsonContentType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        service.ingest(new IngestRequest("burp_base64", "https://example.com", "cmVx", "cmVzcA=="));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
    }

    // ──── Findings endpoint ────

    @Test
    void findings_callsCorrectEndpointWithQueryParams() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[],\"total\":0,\"limit\":50,\"offset\":0,\"has_more\":false}")
                .setHeader("Content-Type", "application/json"));

        FindingsQuery query = new FindingsQuery();
        query.setSearch("test");
        query.setSeverity("high");
        service.findings(query);

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        String path = request.getPath();
        assertTrue(path.startsWith("/api/findings?"), "Path should start with /api/findings?");
        assertTrue(path.contains("limit=50"), "Must include limit param");
        assertTrue(path.contains("offset=0"), "Must include offset param");
        assertTrue(path.contains("search=test"), "Must include search param");
        assertTrue(path.contains("severity=high"), "Must include severity param");
    }

    @Test
    void findings_sendsBearerToken() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[],\"total\":0,\"limit\":50,\"offset\":0,\"has_more\":false}")
                .setHeader("Content-Type", "application/json"));

        service.findings(new FindingsQuery());

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }

    @Test
    void findings_parsesAllFields() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[{"
                        + "\"id\":1,"
                        + "\"http_record_uuids\":[\"abc-123\"],"
                        + "\"scan_uuid\":\"scan-456\","
                        + "\"module_id\":\"xss-scanner\","
                        + "\"module_name\":\"XSS Scanner\","
                        + "\"description\":\"Reflected XSS via parameter 'q'\","
                        + "\"severity\":\"high\","
                        + "\"confidence\":\"firm\","
                        + "\"tags\":[\"xss\",\"reflected\"],"
                        + "\"matched_at\":[\"https://example.com/search?q=test\"],"
                        + "\"found_at\":\"2026-02-16T15:05:00Z\""
                        + "}],\"total\":1,\"limit\":50,\"offset\":0,\"has_more\":false}")
                .setHeader("Content-Type", "application/json"));

        FindingsResponse response = service.findings(new FindingsQuery());

        assertEquals(1, response.data().size());
        var finding = response.data().get(0);
        assertEquals(1, finding.id());
        assertEquals(Severity.HIGH, finding.severity());
        assertEquals("xss-scanner", finding.moduleId());
        assertEquals("XSS Scanner", finding.moduleName());
        assertEquals("Reflected XSS via parameter 'q'", finding.description());
        assertEquals("firm", finding.confidence());
        assertEquals(1, finding.httpRecordUuids().size());
        assertEquals("abc-123", finding.httpRecordUuids().get(0));
        assertEquals(1, finding.matchedAt().size());
        assertEquals("https://example.com/search?q=test", finding.matchedAt().get(0));
        assertEquals("2026-02-16T15:05:00Z", finding.foundAt());
        assertEquals(1, response.total());
        assertFalse(response.hasMore());
    }

    @Test
    void findings_handlesEmptyData() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[],\"total\":0,\"limit\":50,\"offset\":0,\"has_more\":false}")
                .setHeader("Content-Type", "application/json"));

        FindingsResponse response = service.findings(new FindingsQuery());

        assertTrue(response.data().isEmpty());
        assertEquals(0, response.total());
    }

    @Test
    void findings_handlesMultipleFindings() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":["
                        + "{\"id\":1,\"severity\":\"critical\",\"module_id\":\"sqli\",\"module_name\":\"SQLi\","
                        + "\"description\":\"t1\",\"confidence\":\"certain\",\"found_at\":\"ts1\"},"
                        + "{\"id\":2,\"severity\":\"low\",\"module_id\":\"xss\",\"module_name\":\"XSS\","
                        + "\"description\":\"t2\",\"confidence\":\"tentative\",\"found_at\":\"ts2\"}"
                        + "],\"total\":2,\"limit\":50,\"offset\":0,\"has_more\":false}")
                .setHeader("Content-Type", "application/json"));

        FindingsResponse response = service.findings(new FindingsQuery());

        assertEquals(2, response.data().size());
        assertEquals(Severity.CRITICAL, response.data().get(0).severity());
        assertEquals(Severity.LOW, response.data().get(1).severity());
    }

    @Test
    void findings_paginationParams() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[],\"total\":100,\"limit\":25,\"offset\":50,\"has_more\":true}")
                .setHeader("Content-Type", "application/json"));

        FindingsQuery query = new FindingsQuery();
        query.setLimit(25);
        query.setOffset(50);
        FindingsResponse response = service.findings(query);

        assertEquals(100, response.total());
        assertEquals(25, response.limit());
        assertEquals(50, response.offset());
        assertTrue(response.hasMore());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("limit=25"));
        assertTrue(request.getPath().contains("offset=50"));
    }

    // ──── Scan endpoint ────

    @Test
    void scan_callsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setBody("{\"scan_id\":\"abc\",\"status\":\"running\",\"message\":\"started\"}")
                .setHeader("Content-Type", "application/json"));

        service.scan(new ScanRequest("cmVx", null));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/scan-request", request.getPath());
    }

    @Test
    void scan_sendsBearerToken() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setBody("{\"scan_id\":\"abc\",\"status\":\"running\",\"message\":\"started\"}"));

        service.scan(new ScanRequest("cmVx", null));

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }

    @Test
    void scan_sendsCorrectJsonFormat() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setBody("{\"scan_id\":\"abc\",\"status\":\"running\",\"message\":\"started\"}"));

        service.scan(new ScanRequest("cmVxdWVzdA==", "https://example.com"));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("cmVxdWVzdA==", json.get("http_request_base64").getAsString());
        assertEquals("https://example.com", json.get("target_url").getAsString());
    }

    @Test
    void scan_parsesResponse() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(202)
                        .setBody(
                                "{\"scan_id\":\"scan-123\",\"status\":\"running\",\"message\":\"scan-request started for example.com\"}"));

        ScanResponse response = service.scan(new ScanRequest("cmVx", null));

        assertEquals("scan-123", response.scanId());
        assertEquals("running", response.status());
        assertEquals("scan-request started for example.com", response.message());
    }

    @Test
    void scan_409_throwsApiException() {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"error\":\"a scan is already running\"}"));

        VigoliumApiException ex =
                assertThrows(VigoliumApiException.class, () -> service.scan(new ScanRequest("cmVx", null)));
        assertEquals(409, ex.statusCode());
    }

    @Test
    void scan_400_throwsApiException() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"missing raw_request\"}"));

        VigoliumApiException ex =
                assertThrows(VigoliumApiException.class, () -> service.scan(new ScanRequest(null, null)));
        assertEquals(400, ex.statusCode());
    }

    // ──── Retry logic ────

    @Test
    void retries_onServerError_thenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();

        assertEquals("ok", response.status());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void retries_onIOException_thenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();

        assertEquals("ok", response.status());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void retries_maxTwoTimes_thenThrows() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        }

        VigoliumApiException ex = assertThrows(VigoliumApiException.class, () -> service.health());
        assertTrue(ex.getMessage().contains("2 retries"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void noRetry_on4xxClientError() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));

        VigoliumApiException ex = assertThrows(VigoliumApiException.class, () -> service.health());
        assertEquals(401, ex.statusCode());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void noRetry_on403() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));

        VigoliumApiException ex = assertThrows(VigoliumApiException.class, () -> service.health());
        assertEquals(403, ex.statusCode());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void noRetry_on404() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        VigoliumApiException ex = assertThrows(VigoliumApiException.class, () -> service.health());
        assertEquals(404, ex.statusCode());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void noRetry_on429() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        VigoliumApiException ex = assertThrows(VigoliumApiException.class, () -> service.health());
        assertEquals(429, ex.statusCode());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void retries_on502() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("bad gateway"));
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();
        assertEquals("ok", response.status());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void retries_on503() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        HealthResponse response = service.health();
        assertEquals("ok", response.status());
        assertEquals(2, server.getRequestCount());
    }

    // ──── API key supplier ────

    @Test
    void usesCurrentApiKeyFromSupplier() throws Exception {
        var keyHolder = new String[] {"key-1"};
        var dynamicService = new VigoliumApiService(() -> server.url("/").toString(), () -> keyHolder[0], client);

        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));
        dynamicService.health();
        RecordedRequest r1 = server.takeRequest();
        assertEquals("Bearer key-1", r1.getHeader("Authorization"));

        keyHolder[0] = "key-2";
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));
        dynamicService.health();
        RecordedRequest r2 = server.takeRequest();
        assertEquals("Bearer key-2", r2.getHeader("Authorization"));
    }

    // ──── Server URL normalization ────

    @Test
    void stripsTrailingSlashFromServerUrl() throws Exception {
        var svc = new VigoliumApiService(() -> server.url("/").toString(), () -> "key", client);

        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"ok\",\"version\":\"1.0\"}")
                .setHeader("Content-Type", "application/json"));

        svc.health();

        RecordedRequest request = server.takeRequest();
        assertEquals("/health", request.getPath());
    }
}

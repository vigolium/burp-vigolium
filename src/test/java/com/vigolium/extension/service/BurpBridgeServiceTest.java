package com.vigolium.extension.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.sitemap.SiteMapFilter;
import burp.api.montoya.sitemap.SiteMapNode;
import com.google.gson.JsonParser;
import com.vigolium.extension.config.BridgeSettings;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BurpBridgeServiceTest {

    private BurpBridgeService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void enabledBridgeListensOnConfiguredLoopbackPort() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService());

        service.start();

        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("vigolium-burp-bridge"));
        assertTrue(response.body().contains("\"authentication\":\"none\""));
        assertTrue(service.testConnection().get(5, TimeUnit.SECONDS).successful());

        HttpRequest inspect = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/burp-bridge/inspect"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"ref\":\"missing\"}"))
                .build();
        HttpResponse<String> inspectResponse = client.send(inspect, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, inspectResponse.statusCode());
        assertTrue(inspectResponse.body().contains("expired or unknown"), inspectResponse.body());

        HttpRequest crossOriginInspect = HttpRequest.newBuilder(inspect.uri())
                .header("Content-Type", "application/json")
                .header("Origin", "https://attacker.test")
                .POST(HttpRequest.BodyPublishers.ofString("{\"ref\":\"missing\"}"))
                .build();
        assertEquals(
                403,
                client.send(crossOriginInspect, HttpResponse.BodyHandlers.ofString())
                        .statusCode());

        service.shutdown();
        service = null;
        assertThrows(Exception.class, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void bridgeScopeSettingExcludesOutOfScopeItems() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.isBridgeInScopeOnly()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        MontoyaApi api = mock(MontoyaApi.class);
        SiteMap siteMap = mock(SiteMap.class);
        when(api.siteMap()).thenReturn(siteMap);
        List<HttpRequestResponse> items =
                List.of(siteMapItem("https://example.test/in-scope", true), siteMapItem("https://other.test/", false));
        when(siteMap.requestResponses(any(SiteMapFilter.class))).thenAnswer(invocation -> {
            SiteMapFilter filter = invocation.getArgument(0);
            List<HttpRequestResponse> matches = new ArrayList<>();
            for (HttpRequestResponse item : items) {
                SiteMapNode node = mock(SiteMapNode.class);
                when(node.requestResponse()).thenReturn(item);
                if (filter.matches(node)) matches.add(item);
            }
            return matches;
        });

        service = new BurpBridgeService(api, settings, new LogService());
        service.start();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/burp-bridge/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(
                1,
                JsonParser.parseString(response.body())
                        .getAsJsonObject()
                        .get("total")
                        .getAsInt());
        assertTrue(response.body().contains("/in-scope"));
    }

    @Test
    void bridgeAddsIngestCompatibleTrafficToSiteMap() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        MontoyaApi api = mock(MontoyaApi.class);
        SiteMap siteMap = mock(SiteMap.class);
        when(api.siteMap()).thenReturn(siteMap);
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<byte[]> capturedRequest = new AtomicReference<>();
        AtomicReference<byte[]> capturedResponse = new AtomicReference<>();
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        burp.api.montoya.http.message.requests.HttpRequest itemRequest =
                mock(burp.api.montoya.http.message.requests.HttpRequest.class);
        when(item.request()).thenReturn(itemRequest);
        when(itemRequest.url()).thenReturn("https://example.test/saved");

        service = new BurpBridgeService(api, settings, new LogService(), (url, request, response, source) -> {
            capturedUrl.set(url);
            capturedRequest.set(request);
            capturedResponse.set(response);
            return item;
        });
        service.start();

        byte[] rawRequest = "GET /saved HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] rawResponse = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".getBytes(StandardCharsets.ISO_8859_1);
        String body = "{\"input_mode\":\"burp_base64\",\"url\":\"https://example.test/saved\","
                + "\"source\":\"vigolium-db\",\"http_request_base64\":\""
                + Base64.getEncoder().encodeToString(rawRequest)
                + "\",\"http_response_base64\":\""
                + Base64.getEncoder().encodeToString(rawResponse)
                + "\"}";
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/burp-bridge/sitemap"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"added\":1"));
        assertEquals("https://example.test/saved", capturedUrl.get());
        assertArrayEquals(rawRequest, capturedRequest.get());
        assertArrayEquals(rawResponse, capturedResponse.get());
        verify(siteMap).add(item);
    }

    @Test
    void bridgeSendsSuppliedRequestToRepeater() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<byte[]> capturedRequest = new AtomicReference<>();
        AtomicReference<String> capturedTab = new AtomicReference<>();
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), null, (url, request, tabName) -> {
                    capturedUrl.set(url);
                    capturedRequest.set(request);
                    capturedTab.set(tabName);
                });
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        String body = "{\"input_mode\":\"burp_base64\",\"url\":\"https://example.test/probe\","
                + "\"tab_name\":\"idor-1\",\"http_request_base64\":\""
                + Base64.getEncoder().encodeToString(rawRequest) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/repeater", body);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"sent\":1"), response.body());
        assertEquals("https://example.test/probe", capturedUrl.get());
        assertArrayEquals(rawRequest, capturedRequest.get());
        assertEquals("idor-1", capturedTab.get());
    }

    @Test
    void repeaterSendCanAlsoExecuteThroughBurpAndReturnResponse() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<String> tabbed = new AtomicReference<>();
        AtomicReference<HttpMode> executedMode = new AtomicReference<>();
        byte[] rawResponse = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nhi".getBytes(StandardCharsets.ISO_8859_1);
        BurpBridgeService.RepeaterSender repeater = (url, request, tabName) -> tabbed.set(tabName);
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) -> {
            executedMode.set(mode);
            return new BurpBridgeService.SendOutcome(false, true, 200, rawResponse, 12L, null);
        };
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, repeater, sender);
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        String body =
                "{\"url\":\"https://example.test/probe\",\"send\":true,\"http_mode\":\"http1\",\"tab_name\":\"probe\","
                        + "\"http_request_base64\":\"" + Base64.getEncoder().encodeToString(rawRequest) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/repeater", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, json.get("sent").getAsInt());
        assertTrue(json.get("executed").getAsBoolean());
        assertEquals(200, json.get("status_code").getAsInt());
        assertArrayEquals(
                rawResponse,
                Base64.getDecoder().decode(json.get("response_base64").getAsString()));
        assertEquals("probe", tabbed.get(), "the tab must still be staged");
        assertEquals(HttpMode.HTTP_1, executedMode.get());
    }

    @Test
    void repeaterSendSkipsExecutionForOutOfScopeTargetsButStillStagesTab() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.isBridgeInScopeOnly()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<String> tabbed = new AtomicReference<>();
        BurpBridgeService.RepeaterSender repeater = (url, request, tabName) -> tabbed.set(tabName);
        BurpBridgeService.RequestSender sender =
                (url, request, mode, timeout, enforceInScope) -> BurpBridgeService.SendOutcome.outOfScope();
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, repeater, sender);
        service.start();

        String body =
                "{\"url\":\"https://out.test/probe\",\"send\":true,\"tab_name\":\"probe\",\"http_request_base64\":\""
                        + Base64.getEncoder()
                                .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                        + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/repeater", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, json.get("sent").getAsInt());
        assertFalse(json.get("executed").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("out of Burp scope"));
        assertFalse(json.has("status_code"), "no response when auto-send is skipped");
        assertEquals("probe", tabbed.get(), "the tab must still be staged even when auto-send is skipped");
    }

    @Test
    void repeaterSendRejectsRelativeUrlsAndUnknownRefs() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);
        AtomicReference<String> sent = new AtomicReference<>();
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), null, (url, request, tabName) -> sent.set(url));
        service.start();

        HttpResponse<String> missingUrl = postJson(port, "/api/burp-bridge/repeater", "{}");
        assertEquals(400, missingUrl.statusCode());
        assertTrue(missingUrl.body().contains("url is required"), missingUrl.body());

        HttpResponse<String> relative = postJson(
                port,
                "/api/burp-bridge/repeater",
                "{\"url\":\"/probe\",\"http_request_base64\":\""
                        + Base64.getEncoder()
                                .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                        + "\"}");
        assertEquals(400, relative.statusCode());
        assertTrue(relative.body().contains("absolute http or https"), relative.body());

        HttpResponse<String> unknownRef = postJson(port, "/api/burp-bridge/repeater", "{\"ref\":\"nope\"}");
        assertEquals(400, unknownRef.statusCode());
        assertTrue(unknownRef.body().contains("expired or unknown"), unknownRef.body());

        assertNull(sent.get(), "no request should reach Repeater when validation fails");
    }

    @Test
    void repeaterSendIsRateLimitedPerMinute() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);
        AtomicInteger sends = new AtomicInteger();
        service = new BurpBridgeService(
                mock(MontoyaApi.class),
                settings,
                new LogService(),
                null,
                (url, request, tabName) -> sends.incrementAndGet());
        service.start();

        String body = "{\"url\":\"https://example.test/probe\",\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        for (int i = 0; i < 30; i++) {
            assertEquals(200, postJson(port, "/api/burp-bridge/repeater", body).statusCode(), "send " + i);
        }

        HttpResponse<String> throttled = postJson(port, "/api/burp-bridge/repeater", body);
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.body().contains("per minute"), throttled.body());
        assertEquals(30, sends.get(), "throttled request must not reach Repeater");
    }

    @Test
    void bridgeSendsRequestThroughBurpAndReturnsResponse() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<byte[]> capturedRequest = new AtomicReference<>();
        AtomicReference<HttpMode> capturedMode = new AtomicReference<>();
        AtomicReference<Boolean> capturedEnforce = new AtomicReference<>();
        byte[] rawResponse =
                "HTTP/1.1 418 I'm a teapot\r\nContent-Length: 2\r\n\r\nhi".getBytes(StandardCharsets.ISO_8859_1);
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) -> {
            capturedUrl.set(url);
            capturedRequest.set(request);
            capturedMode.set(mode);
            capturedEnforce.set(enforceInScope);
            return new BurpBridgeService.SendOutcome(false, true, 418, rawResponse, 42L, null);
        };
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, null, sender);
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        String body = "{\"url\":\"https://example.test/probe\",\"http_mode\":\"http1\",\"http_request_base64\":\""
                + Base64.getEncoder().encodeToString(rawRequest) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/send", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, json.get("sent").getAsInt());
        assertEquals(418, json.get("status_code").getAsInt());
        assertEquals("HTTP_1", json.get("http_mode").getAsString());
        assertEquals(42L, json.get("elapsed_ms").getAsLong());
        assertArrayEquals(
                rawResponse,
                Base64.getDecoder().decode(json.get("response_base64").getAsString()));
        assertArrayEquals(rawRequest, capturedRequest.get());
        assertEquals(HttpMode.HTTP_1, capturedMode.get());
        assertEquals("https://example.test/probe", capturedUrl.get());
    }

    @Test
    void sendCanAlsoRecordTheExchangeInSiteMap() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        MontoyaApi api = mock(MontoyaApi.class);
        SiteMap siteMap = mock(SiteMap.class);
        when(api.siteMap()).thenReturn(siteMap);
        AtomicReference<byte[]> savedResponse = new AtomicReference<>();
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        burp.api.montoya.http.message.requests.HttpRequest itemRequest =
                mock(burp.api.montoya.http.message.requests.HttpRequest.class);
        when(item.request()).thenReturn(itemRequest);
        when(itemRequest.url()).thenReturn("https://example.test/probe");

        byte[] rawResponse = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".getBytes(StandardCharsets.ISO_8859_1);
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) ->
                new BurpBridgeService.SendOutcome(false, true, 200, rawResponse, 5L, null);
        BurpBridgeService.SiteMapItemFactory factory = (url, request, response, source) -> {
            savedResponse.set(response);
            return item;
        };
        service = new BurpBridgeService(api, settings, new LogService(), factory, null, sender);
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        String body = "{\"url\":\"https://example.test/probe\",\"add_to_sitemap\":true,\"http_request_base64\":\""
                + Base64.getEncoder().encodeToString(rawRequest) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/send", body);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"added_to_sitemap\":true"), response.body());
        assertArrayEquals(rawResponse, savedResponse.get());
        verify(siteMap).add(item);
    }

    @Test
    void sendRejectsOutOfScopeTargetsWhenInScopeOnlyIsEnabled() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.isBridgeInScopeOnly()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<Boolean> enforceSeen = new AtomicReference<>();
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) -> {
            enforceSeen.set(enforceInScope);
            return enforceInScope ? BurpBridgeService.SendOutcome.outOfScope() : null;
        };
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, null, sender);
        service.start();

        String body = "{\"url\":\"https://out-of-scope.test/probe\",\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/send", body);

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("out of Burp scope"), response.body());
        assertEquals(Boolean.TRUE, enforceSeen.get());
    }

    @Test
    void sendReportsTargetConnectionFailureWithoutServerError() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) ->
                new BurpBridgeService.SendOutcome(false, false, 0, new byte[0], null, "connection refused");
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, null, sender);
        service.start();

        String body = "{\"url\":\"https://example.test/probe\",\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/send", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(0, json.get("sent").getAsInt());
        assertEquals("connection refused", json.get("error").getAsString());
        assertFalse(json.has("status_code"));
    }

    @Test
    void sendRejectsUnknownHttpMode() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);
        AtomicReference<String> sent = new AtomicReference<>();
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) -> {
            sent.set(url);
            return new BurpBridgeService.SendOutcome(false, true, 200, new byte[0], null, null);
        };
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService(), null, null, sender);
        service.start();

        String body = "{\"url\":\"https://example.test/probe\",\"http_mode\":\"http3\",\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/send", body);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("http_mode must be one of"), response.body());
        assertNull(sent.get(), "invalid mode must be rejected before sending");
    }

    @Test
    void organizerStoresSuppliedRequestAndResponsePair() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<byte[]> storedResponse = new AtomicReference<>();
        AtomicReference<HttpRequestResponse> organized = new AtomicReference<>();
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        burp.api.montoya.http.message.requests.HttpRequest itemRequest =
                mock(burp.api.montoya.http.message.requests.HttpRequest.class);
        when(item.request()).thenReturn(itemRequest);
        when(itemRequest.url()).thenReturn("https://example.test/probe");

        BurpBridgeService.SiteMapItemFactory factory = (url, request, response, source) -> {
            storedResponse.set(response);
            return item;
        };
        BurpBridgeService.OrganizerSender organizer = organized::set;
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), factory, null, null, organizer);
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] rawResponse = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".getBytes(StandardCharsets.ISO_8859_1);
        String body = "{\"url\":\"https://example.test/probe\",\"http_request_base64\":\""
                + Base64.getEncoder().encodeToString(rawRequest) + "\",\"http_response_base64\":\""
                + Base64.getEncoder().encodeToString(rawResponse) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/organizer", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, json.get("added").getAsInt());
        assertTrue(json.get("has_response").getAsBoolean());
        assertArrayEquals(rawResponse, storedResponse.get());
        assertEquals(item, organized.get());
    }

    @Test
    void organizerCanExecuteThroughBurpThenStoreTheExchange() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<byte[]> storedResponse = new AtomicReference<>();
        AtomicReference<HttpRequestResponse> organized = new AtomicReference<>();
        byte[] fetched = "HTTP/1.1 201 Created\r\nContent-Length: 2\r\n\r\nhi".getBytes(StandardCharsets.ISO_8859_1);
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        BurpBridgeService.RequestSender sender = (url, request, mode, timeout, enforceInScope) ->
                new BurpBridgeService.SendOutcome(false, true, 201, fetched, 9L, null);
        BurpBridgeService.SiteMapItemFactory factory = (url, request, response, source) -> {
            storedResponse.set(response);
            return item;
        };
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), factory, null, sender, organized::set);
        service.start();

        byte[] rawRequest = "GET /probe HTTP/1.1\r\nHost: example.test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        String body =
                "{\"url\":\"https://example.test/probe\",\"send\":true,\"http_mode\":\"http1\",\"http_request_base64\":\""
                        + Base64.getEncoder().encodeToString(rawRequest) + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/organizer", body);

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, json.get("added").getAsInt());
        assertTrue(json.get("has_response").getAsBoolean());
        assertEquals(201, json.get("status_code").getAsInt());
        assertArrayEquals(
                fetched, Base64.getDecoder().decode(json.get("response_base64").getAsString()));
        assertArrayEquals(fetched, storedResponse.get(), "the fetched response must be stored in the Organizer item");
        assertEquals(item, organized.get());
    }

    @Test
    void organizerAppliesCustomNoteAndHighlight() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        HttpRequestResponse item = mock(HttpRequestResponse.class);
        Annotations annotations = mock(Annotations.class);
        when(item.annotations()).thenReturn(annotations);
        AtomicReference<HttpRequestResponse> organized = new AtomicReference<>();
        BurpBridgeService.SiteMapItemFactory factory = (url, request, response, source) -> item;
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), factory, null, null, organized::set);
        service.start();

        String body = "{\"url\":\"https://example.test/probe\",\"notes\":\"recon-batch-1\",\"highlight\":\"red\","
                + "\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/organizer", body);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("recon-batch-1"), response.body());
        verify(annotations).setNotes("recon-batch-1");
        verify(annotations).setHighlightColor(HighlightColor.RED);
        assertEquals(item, organized.get());
    }

    @Test
    void organizerRejectsUnknownHighlightColor() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<HttpRequestResponse> organized = new AtomicReference<>();
        BurpBridgeService.SiteMapItemFactory factory =
                (url, request, response, source) -> mock(HttpRequestResponse.class);
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), factory, null, null, organized::set);
        service.start();

        String body = "{\"url\":\"https://example.test/probe\",\"highlight\":\"chartreuse\",\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/organizer", body);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("highlight must be one of"), response.body());
        assertNull(organized.get(), "nothing must be organized when the highlight is invalid");
    }

    @Test
    void organizerSendRejectsOutOfScopeTargetsWhenInScopeOnlyIsEnabled() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.isBridgeInScopeOnly()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        AtomicReference<HttpRequestResponse> organized = new AtomicReference<>();
        BurpBridgeService.RequestSender sender =
                (url, request, mode, timeout, enforceInScope) -> BurpBridgeService.SendOutcome.outOfScope();
        service = new BurpBridgeService(
                mock(MontoyaApi.class), settings, new LogService(), null, null, sender, organized::set);
        service.start();

        String body = "{\"url\":\"https://out.test/probe\",\"send\":true,\"http_request_base64\":\""
                + Base64.getEncoder()
                        .encodeToString("GET /probe HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                + "\"}";
        HttpResponse<String> response = postJson(port, "/api/burp-bridge/organizer", body);

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("out of Burp scope"), response.body());
        assertNull(organized.get(), "nothing must be organized when the send is scope-blocked");
    }

    @Test
    void bridgeRejectsDnsRebindingHosts() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);
        service = new BurpBridgeService(mock(MontoyaApi.class), settings, new LogService());
        service.start();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            output.write(("GET /health HTTP/1.1\r\nHost: attacker.test:" + port + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertTrue(reader.readLine().contains("403"));
        }
    }

    @Test
    void hostFilterOnlyMatchesTheRequestHostname() throws Exception {
        int port = freeLoopbackPort();
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.isBridgeEnabled()).thenReturn(true);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:" + port);

        MontoyaApi api = mock(MontoyaApi.class);
        SiteMap siteMap = mock(SiteMap.class);
        when(api.siteMap()).thenReturn(siteMap);
        List<HttpRequestResponse> items = List.of(
                siteMapItem("https://attacker.test/?next=example.com", true),
                siteMapItem("https://example.com/expected", true));
        when(siteMap.requestResponses(any(SiteMapFilter.class))).thenAnswer(invocation -> {
            SiteMapFilter filter = invocation.getArgument(0);
            List<HttpRequestResponse> matches = new ArrayList<>();
            for (HttpRequestResponse item : items) {
                SiteMapNode node = mock(SiteMapNode.class);
                when(node.requestResponse()).thenReturn(item);
                if (filter.matches(node)) matches.add(item);
            }
            return matches;
        });

        service = new BurpBridgeService(api, settings, new LogService());
        service.start();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/burp-bridge/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"host\":\"example.com\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(
                1,
                JsonParser.parseString(response.body())
                        .getAsJsonObject()
                        .get("total")
                        .getAsInt());
        assertTrue(response.body().contains("/expected"));
    }

    @Test
    void unlimitedSearchesAreCappedToReferenceCacheCapacity() {
        assertEquals(10_000, BurpBridgeService.cappedEndIndex(20_000, 0, 0));
        assertEquals(15_000, BurpBridgeService.cappedEndIndex(20_000, 5_000, 0));
        assertEquals(5_050, BurpBridgeService.cappedEndIndex(20_000, 5_000, 50));
    }

    private static HttpResponse<String> postJson(int port, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequestResponse siteMapItem(String url, boolean inScope) {
        burp.api.montoya.http.message.requests.HttpRequest request =
                mock(burp.api.montoya.http.message.requests.HttpRequest.class);
        when(request.isInScope()).thenReturn(inScope);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn(url);
        when(request.path()).thenReturn(URI.create(url).getPath());
        ByteArray bytes = mock(ByteArray.class);
        when(bytes.getBytes()).thenReturn("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        when(request.toByteArray()).thenReturn(bytes);

        HttpRequestResponse item = mock(HttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.hasResponse()).thenReturn(false);
        return item;
    }

    private static int freeLoopbackPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}

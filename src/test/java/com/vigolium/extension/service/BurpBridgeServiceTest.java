package com.vigolium.extension.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
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

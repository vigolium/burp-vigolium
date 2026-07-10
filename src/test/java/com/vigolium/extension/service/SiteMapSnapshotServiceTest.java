package com.vigolium.extension.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.sitemap.SiteMap;
import com.vigolium.extension.config.SnapshotSettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SiteMapSnapshotServiceTest {

    private SnapshotSettings settings;
    private VigoliumApiService apiService;
    private SiteMapSnapshotService service;
    private SiteMap siteMap;

    @BeforeEach
    void setUp() {
        MontoyaApi api = mock(MontoyaApi.class);
        siteMap = mock(SiteMap.class);
        settings = mock(SnapshotSettings.class);
        apiService = mock(VigoliumApiService.class);
        when(api.siteMap()).thenReturn(siteMap);
        when(settings.isSnapshotAutoEnabled()).thenReturn(false);
        when(settings.isSnapshotInScopeOnly()).thenReturn(true);
        when(settings.getSnapshotIntervalMinutes()).thenReturn(5);
        when(apiService.isConfigured()).thenReturn(true);
        HttpRequestResponse inScope = item(true);
        HttpRequestResponse outOfScope = item(false);
        when(siteMap.requestResponses()).thenReturn(List.of(inScope, outOfScope));
        service = new SiteMapSnapshotService(api, settings, apiService, new LogService());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void snapshotUploadsInScopeItemsAndDeduplicatesNextRun() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        when(apiService.snapshotSiteMap(any())).thenAnswer(inv -> {
            first.countDown();
            return new SnapshotChunkResponse(1, 1, 0, 0, 0, List.of());
        });

        service.snapshotNow("Test");
        assertTrue(first.await(3, TimeUnit.SECONDS));
        service.snapshotNow("Test");
        Thread.sleep(300);

        verify(apiService, times(1)).snapshotSiteMap(any());
    }

    @Test
    void changingDestinationInvalidatesSynchronizedFingerprints() throws Exception {
        when(apiService.destinationIdentity()).thenReturn("destination-a", "destination-b");
        CountDownLatch uploads = new CountDownLatch(2);
        when(apiService.snapshotSiteMap(any())).thenAnswer(inv -> {
            uploads.countDown();
            return new SnapshotChunkResponse(1, 1, 0, 0, 0, List.of());
        });

        service.snapshotNow("First destination");
        service.snapshotNow("Second destination");

        assertTrue(uploads.await(3, TimeUnit.SECONDS));
        verify(apiService, times(2)).snapshotSiteMap(any());
    }

    @Test
    void snapshotChunksAreBoundedByRawByteSizeBeforeEncoding() throws Exception {
        int recordBytes = 3 * 1024 * 1024;
        List<HttpRequestResponse> largeItems = List.of(
                item("https://example.test/one", recordBytes),
                item("https://example.test/two", recordBytes),
                item("https://example.test/three", recordBytes));
        when(siteMap.requestResponses()).thenReturn(largeItems);
        List<SiteMapSnapshotRequest> requests = new ArrayList<>();
        CountDownLatch uploads = new CountDownLatch(2);
        when(apiService.snapshotSiteMap(any())).thenAnswer(inv -> {
            requests.add(inv.getArgument(0));
            uploads.countDown();
            return new SnapshotChunkResponse(1, 1, 0, 0, 0, List.of());
        });

        service.snapshotNow("Byte bounded");

        assertTrue(uploads.await(5, TimeUnit.SECONDS));
        assertEquals(2, requests.size());
        assertEquals(2, requests.get(0).records().size());
        assertEquals(1, requests.get(1).records().size());
        assertTrue(!requests.get(0).finalChunk());
        assertTrue(requests.get(1).finalChunk());
    }

    private static HttpRequestResponse item(boolean inScope) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.isInScope()).thenReturn(inScope);
        when(request.url()).thenReturn("https://example.test/" + inScope);
        ByteArray requestBytes = mock(ByteArray.class);
        when(requestBytes.getBytes())
                .thenReturn(("GET / HTTP/1.1\r\nHost: example.test\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        when(request.toByteArray()).thenReturn(requestBytes);

        HttpResponse response = mock(HttpResponse.class);
        ByteArray responseBytes = mock(ByteArray.class);
        when(responseBytes.getBytes()).thenReturn("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        when(response.toByteArray()).thenReturn(responseBytes);

        HttpRequestResponse item = mock(HttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.hasResponse()).thenReturn(true);
        when(item.response()).thenReturn(response);
        return item;
    }

    private static HttpRequestResponse item(String url, int requestSize) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.isInScope()).thenReturn(true);
        when(request.url()).thenReturn(url);
        ByteArray requestBytes = mock(ByteArray.class);
        byte[] bytes = new byte[requestSize];
        Arrays.fill(bytes, (byte) 'a');
        when(requestBytes.length()).thenReturn(bytes.length);
        when(requestBytes.getBytes()).thenReturn(bytes);
        when(request.toByteArray()).thenReturn(requestBytes);

        HttpRequestResponse item = mock(HttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.hasResponse()).thenReturn(false);
        return item;
    }
}

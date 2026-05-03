package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vigolium.extension.config.FilterSettings;
import com.vigolium.extension.config.ProxySettings;
import com.vigolium.extension.config.ServerSettings;
import com.vigolium.extension.service.HealthResponse;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.VigoliumApiService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsControllerTest {

    private ServerSettings serverSettings;
    private ProxySettings proxySettings;
    private FilterSettings filterSettings;
    private VigoliumApiService apiService;
    private LogService logService;
    private SettingsController controller;

    @BeforeEach
    void setUp() {
        serverSettings = mock(ServerSettings.class);
        proxySettings = mock(ProxySettings.class);
        filterSettings = mock(FilterSettings.class);
        apiService = mock(VigoliumApiService.class);
        logService = new LogService();

        controller = new SettingsController(serverSettings, proxySettings, filterSettings, apiService, logService);
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }

    // --- Test Connection success ---

    @Test
    void testConnection_success_callsCallback() throws Exception {
        HealthResponse healthResponse = new HealthResponse("ok", "1.0", 45);
        when(apiService.health()).thenReturn(healthResponse);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthResponse> captured = new AtomicReference<>();

        controller.setOnConnectionSuccess(response -> {
            captured.set(response);
            latch.countDown();
        });

        controller.testConnection();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(healthResponse, captured.get());
    }

    @Test
    void testConnection_success_logsConnection() throws Exception {
        when(apiService.health()).thenReturn(new HealthResponse("ok", "2.0", 30));

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnConnectionSuccess(r -> latch.countDown());

        controller.testConnection();
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertTrue(logService.getEntries().stream()
                .anyMatch(e -> e.level() == LogService.Level.INFO && e.message().contains("Connected to Vigolium")));
    }

    // --- Test Connection failure ---

    @Test
    void testConnection_failure_callsFailureCallback() throws Exception {
        when(apiService.health()).thenThrow(new RuntimeException("connection refused"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedError = new AtomicReference<>();

        controller.setOnConnectionFailure(msg -> {
            capturedError.set(msg);
            latch.countDown();
        });

        controller.testConnection();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("connection refused", capturedError.get());
    }

    @Test
    void testConnection_failure_logsError() throws Exception {
        when(apiService.health()).thenThrow(new RuntimeException("timeout"));

        CountDownLatch latch = new CountDownLatch(1);
        controller.setOnConnectionFailure(msg -> latch.countDown());

        controller.testConnection();
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertTrue(logService.getEntries().stream()
                .anyMatch(
                        e -> e.level() == LogService.Level.ERROR && e.message().contains("Connection failed")));
    }

    // --- Test Connection testing callback ---

    @Test
    void testConnection_firesTestingCallback() throws Exception {
        when(apiService.health()).thenReturn(new HealthResponse("ok", "1.0", 10));

        CountDownLatch doneLatch = new CountDownLatch(1);
        controller.setOnConnectionSuccess(r -> doneLatch.countDown());

        CountDownLatch testingLatch = new CountDownLatch(1);
        controller.setOnConnectionTesting(testing -> {
            if (testing) testingLatch.countDown();
        });

        controller.testConnection();

        assertTrue(testingLatch.await(3, TimeUnit.SECONDS), "Testing callback should fire with true");
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));
    }

    // --- Background execution (not on EDT) ---

    @Test
    void testConnection_executesInBackground() throws Exception {
        CountDownLatch apiLatch = new CountDownLatch(1);
        when(apiService.health()).thenAnswer(inv -> {
            assertNotEquals("AWT-EventQueue-0", Thread.currentThread().getName(), "API call should not be on EDT");
            apiLatch.countDown();
            return new HealthResponse("ok", "1.0", 10);
        });

        controller.testConnection();
        assertTrue(apiLatch.await(3, TimeUnit.SECONDS));
    }

    // --- Settings save ---

    @Test
    void saveServerUrl_delegatesToSettings() {
        controller.saveServerUrl("https://vigolium.example.com");
        verify(serverSettings).setServerUrl("https://vigolium.example.com");
    }

    @Test
    void saveApiKey_delegatesToSettings() {
        controller.saveApiKey("secret-key");
        verify(serverSettings).setApiKey("secret-key");
    }

    @Test
    void setProxyEnabled_delegatesToSettings() {
        controller.setProxyEnabled(true);
        verify(proxySettings).setProxyEnabled(true);
    }

    @Test
    void setProxyEnabled_logsStateChange() {
        controller.setProxyEnabled(true);
        assertTrue(logService.getEntries().stream().anyMatch(e -> e.message().contains("Proxy mode enabled")));

        controller.setProxyEnabled(false);
        assertTrue(logService.getEntries().stream().anyMatch(e -> e.message().contains("Proxy mode disabled")));
    }
}

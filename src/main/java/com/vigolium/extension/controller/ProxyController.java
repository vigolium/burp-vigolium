package com.vigolium.extension.controller;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import com.vigolium.extension.config.FilterSettings;
import com.vigolium.extension.config.ProxySettings;
import com.vigolium.extension.filter.FilterEngine;
import com.vigolium.extension.service.IngestRequest;
import com.vigolium.extension.service.LogService;
import com.vigolium.extension.service.RequestCounters;
import com.vigolium.extension.service.VigoliumApiService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyController implements ProxyRequestHandler, ProxyResponseHandler {

    private final ProxySettings proxySettings;
    private final FilterSettings filterSettings;
    private final FilterEngine filterEngine;
    private final VigoliumApiService apiService;
    private final LogService logService;
    private final RequestCounters counters;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "vigolium-proxy");
        t.setDaemon(true);
        return t;
    });

    public ProxyController(
            ProxySettings proxySettings,
            FilterSettings filterSettings,
            FilterEngine filterEngine,
            VigoliumApiService apiService,
            LogService logService,
            RequestCounters counters) {
        this.proxySettings = proxySettings;
        this.filterSettings = filterSettings;
        this.filterEngine = filterEngine;
        this.apiService = apiService;
        this.logService = logService;
        this.counters = counters;
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {
        try {
            if (!proxySettings.isProxyEnabled()) {
                return ProxyResponseReceivedAction.continueWith(interceptedResponse);
            }

            if (proxySettings.isInScopeOnly()
                    && !interceptedResponse.initiatingRequest().isInScope()) {
                return ProxyResponseReceivedAction.continueWith(interceptedResponse);
            }

            HttpRequestResponse requestResponse = HttpRequestResponse.httpRequestResponse(
                    interceptedResponse.initiatingRequest(), interceptedResponse);

            processRequestResponse(requestResponse);
        } catch (Exception e) {
            logService.addLog(LogService.Level.ERROR, "[Proxy] Handler error: " + e.getMessage());
        }

        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }

    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedResponse interceptedResponse) {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }

    void processRequestResponse(HttpRequestResponse requestResponse) {
        if (!apiService.isConfigured()) {
            logService.addLog(LogService.Level.ERROR, "[Proxy] Skipped: server URL not configured");
            return;
        }

        if (!filterEngine.evaluate(filterSettings.getProxyFilterRules(), requestResponse)) {
            return;
        }

        // Extract data synchronously while proxy objects are still valid
        IngestRequest ingestRequest = IngestRequest.fromRequestResponse(requestResponse);

        counters.incrementPending();

        executor.submit(() -> {
            try {
                apiService.ingest(ingestRequest);
                counters.markSent();
                logService.addLog(LogService.Level.INFO, "[Proxy] Sent 1 request");
            } catch (Exception e) {
                counters.markFailed();
                logService.addLog(LogService.Level.ERROR, "[Proxy] Request failed: " + e.getMessage());
            }
        });
    }

    public void resetCounters() {
        counters.reset();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

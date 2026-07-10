package com.vigolium.extension.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import com.vigolium.extension.config.HotkeySettings;
import com.vigolium.extension.service.RequestDispatchService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HotkeyControllerTest {

    private MontoyaApi api;
    private UserInterface userInterface;
    private HotkeySettings settings;
    private RequestDispatchService dispatcher;
    private Registration ingestRegistration;
    private Registration scanRegistration;
    private Registration agentScanRegistration;
    private Registration updatedRegistration;
    private HotkeyController controller;
    private MontoyaObjectFactory originalObjectFactory;

    @BeforeEach
    void setUp() {
        api = mock(MontoyaApi.class);
        userInterface = mock(UserInterface.class);
        settings = mock(HotkeySettings.class);
        dispatcher = mock(RequestDispatchService.class);
        ingestRegistration = mock(Registration.class);
        scanRegistration = mock(Registration.class);
        agentScanRegistration = mock(Registration.class);
        updatedRegistration = mock(Registration.class);

        originalObjectFactory = ObjectFactoryLocator.FACTORY;
        MontoyaObjectFactory objectFactory = mock(MontoyaObjectFactory.class);
        when(objectFactory.hotkey(any(), any()))
                .thenAnswer(invocation -> new TestHotKey(invocation.getArgument(0), invocation.getArgument(1)));
        ObjectFactoryLocator.FACTORY = objectFactory;

        when(api.userInterface()).thenReturn(userInterface);
        when(settings.getIngestHotkey()).thenReturn("Ctrl+Alt+V");
        when(settings.getScanHotkey()).thenReturn("Ctrl+Alt+R");
        when(settings.getAgentScanHotkey()).thenReturn("Ctrl+Alt+A");
        when(settings.getSnapshotSitemapHotkey()).thenReturn("Ctrl+Alt+S");
        when(userInterface.registerHotKeyHandler(any(HotKey.class), any(HotKeyHandler.class)))
                .thenReturn(ingestRegistration, scanRegistration, agentScanRegistration, updatedRegistration);

        controller = new HotkeyController(api, settings, dispatcher);
    }

    @AfterEach
    void tearDown() {
        ObjectFactoryLocator.FACTORY = originalObjectFactory;
    }

    @Test
    void register_usesSingleAllContextsRegistrationPerAction() {
        controller.register();

        verify(userInterface, times(3)).registerHotKeyHandler(any(HotKey.class), any(HotKeyHandler.class));
        verify(userInterface, never())
                .registerHotKeyHandler(any(HotKeyContext.class), any(HotKey.class), any(HotKeyHandler.class));
    }

    @Test
    void tableSelection_dispatchesSelectedRows() {
        controller.register();
        RegisteredHotkey ingest = registeredHotkey("Vigolium: Send to Ingestion");
        HttpRequestResponse first = mock(HttpRequestResponse.class);
        HttpRequestResponse second = mock(HttpRequestResponse.class);
        List<HttpRequestResponse> selected = List.of(first, second);
        HotKeyEvent event = mock(HotKeyEvent.class);
        when(event.selectedRequestResponses()).thenReturn(selected);
        when(event.messageEditorRequestResponse()).thenReturn(Optional.empty());

        ingest.handler().handle(event);

        verify(dispatcher).sendToIngestion(selected, "Hotkey");
    }

    @Test
    void emptyTableSelection_doesNotDispatch() {
        controller.register();
        RegisteredHotkey ingest = registeredHotkey("Vigolium: Send to Ingestion");
        HotKeyEvent event = mock(HotKeyEvent.class);
        when(event.selectedRequestResponses()).thenReturn(List.of());
        when(event.messageEditorRequestResponse()).thenReturn(Optional.empty());

        ingest.handler().handle(event);

        verify(dispatcher, never()).sendToIngestion(any(), any());
    }

    @Test
    void updateHotkey_replacesPreviousRegistration() {
        controller.register();
        when(settings.getIngestHotkey()).thenReturn("Ctrl+Shift+I");

        controller.updateIngestHotkey("Ctrl+Shift+I");

        verify(settings).setIngestHotkey("Ctrl+Shift+I");
        verify(ingestRegistration).deregister();

        ArgumentCaptor<HotKey> hotKeys = ArgumentCaptor.forClass(HotKey.class);
        verify(userInterface, times(4)).registerHotKeyHandler(hotKeys.capture(), any(HotKeyHandler.class));
        assertEquals("Ctrl+Shift+I", hotKeys.getAllValues().get(3).hotkey());
    }

    @Test
    void shutdown_deregistersEveryAction() {
        controller.register();

        controller.shutdown();

        verify(ingestRegistration).deregister();
        verify(scanRegistration).deregister();
        verify(agentScanRegistration).deregister();
    }

    @Test
    void snapshotHotkeyRunsWithoutASelection() {
        Registration snapshotRegistration = mock(Registration.class);
        when(userInterface.registerHotKeyHandler(any(HotKey.class), any(HotKeyHandler.class)))
                .thenReturn(ingestRegistration, scanRegistration, agentScanRegistration, snapshotRegistration);
        AtomicBoolean invoked = new AtomicBoolean();
        controller = new HotkeyController(api, settings, dispatcher, () -> invoked.set(true));
        controller.register();

        ArgumentCaptor<HotKey> hotKeys = ArgumentCaptor.forClass(HotKey.class);
        ArgumentCaptor<HotKeyHandler> handlers = ArgumentCaptor.forClass(HotKeyHandler.class);
        verify(userInterface, times(4)).registerHotKeyHandler(hotKeys.capture(), handlers.capture());
        for (int i = 0; i < hotKeys.getAllValues().size(); i++) {
            if (hotKeys.getAllValues().get(i).name().equals("Vigolium: Snapshot Target Site Map")) {
                handlers.getAllValues().get(i).handle(mock(HotKeyEvent.class));
            }
        }
        assertEquals(true, invoked.get());
    }

    private RegisteredHotkey registeredHotkey(String name) {
        ArgumentCaptor<HotKey> hotKeys = ArgumentCaptor.forClass(HotKey.class);
        ArgumentCaptor<HotKeyHandler> handlers = ArgumentCaptor.forClass(HotKeyHandler.class);
        verify(userInterface, times(3)).registerHotKeyHandler(hotKeys.capture(), handlers.capture());

        for (int i = 0; i < hotKeys.getAllValues().size(); i++) {
            if (hotKeys.getAllValues().get(i).name().equals(name)) {
                return new RegisteredHotkey(
                        hotKeys.getAllValues().get(i), handlers.getAllValues().get(i));
            }
        }
        throw new AssertionError("Hotkey not registered: " + name);
    }

    private record RegisteredHotkey(HotKey hotKey, HotKeyHandler handler) {}

    private record TestHotKey(String name, String hotkey) implements HotKey {}
}

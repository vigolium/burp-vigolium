package com.vigolium.extension.config;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.filter.FilterRule;
import com.vigolium.extension.filter.MatchType;
import com.vigolium.extension.filter.Operator;
import com.vigolium.extension.filter.Relationship;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VigoliumSettingsImplTest {

    private MapPreferences preferences;
    private VigoliumSettingsImpl settings;

    @BeforeEach
    void setUp() {
        preferences = new MapPreferences();
        settings = new VigoliumSettingsImpl(preferences);
    }

    @Test
    void serverUrl_defaultValue() {
        assertEquals("http://127.0.0.1:9002", settings.getServerUrl());
    }

    @Test
    void serverUrl_setAndGet() {
        settings.setServerUrl("https://example.com/api");
        assertEquals("https://example.com/api", settings.getServerUrl());
    }

    @Test
    void apiKey_defaultEmpty() {
        assertEquals("", settings.getApiKey());
    }

    @Test
    void apiKey_setAndGet() {
        settings.setApiKey("secret-key");
        assertEquals("secret-key", settings.getApiKey());
    }

    @Test
    void proxyEnabled_defaultFalse() {
        assertFalse(settings.isProxyEnabled());
    }

    @Test
    void proxyEnabled_setAndGet() {
        settings.setProxyEnabled(true);
        assertTrue(settings.isProxyEnabled());
    }

    @Test
    void proxyFilterRules_defaultRules() {
        List<FilterRule> rules = settings.getProxyFilterRules();
        assertEquals(2, rules.size());
        assertEquals(MatchType.FILE_EXTENSION, rules.get(0).getMatchType());
        assertEquals(Relationship.DOES_NOT_MATCH, rules.get(0).getRelationship());
        assertTrue(rules.get(0).isEnabled());
        assertNull(rules.get(0).getOperator());
    }

    @Test
    void proxyFilterRules_serializationRoundTrip() {
        List<FilterRule> rules = List.of(
                new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "^https://.*"),
                new FilterRule(false, Operator.AND, MatchType.HOST, Relationship.DOES_NOT_MATCH, "example.com"));
        settings.setProxyFilterRules(rules);

        List<FilterRule> loaded = settings.getProxyFilterRules();
        assertEquals(2, loaded.size());
        assertEquals(MatchType.URL, loaded.get(0).getMatchType());
        assertEquals("^https://.*", loaded.get(0).getCondition());
        assertEquals(Operator.AND, loaded.get(1).getOperator());
        assertFalse(loaded.get(1).isEnabled());
    }

    // --- HotkeySettings ---

    @Test
    void ingestHotkey_default() {
        assertEquals("Ctrl+Alt+V", settings.getIngestHotkey());
    }

    @Test
    void ingestHotkey_setAndGet() {
        settings.setIngestHotkey("Ctrl+Shift+I");
        assertEquals("Ctrl+Shift+I", settings.getIngestHotkey());
    }

    @Test
    void scanHotkey_default() {
        assertEquals("Ctrl+Alt+R", settings.getScanHotkey());
    }

    @Test
    void scanHotkey_setAndGet() {
        settings.setScanHotkey("Ctrl+Shift+S");
        assertEquals("Ctrl+Shift+S", settings.getScanHotkey());
    }

    @Test
    void changeListener_firesOnSet() {
        AtomicInteger count = new AtomicInteger();
        settings.addChangeListener(count::incrementAndGet);

        settings.setServerUrl("url");
        settings.setApiKey("key");
        settings.setProxyEnabled(true);
        settings.setProxyFilterRules(List.of());
        settings.setIngestHotkey("Ctrl+Shift+I");
        settings.setScanHotkey("Ctrl+Shift+S");

        assertEquals(6, count.get());
    }

    @Test
    void multipleChangeListeners() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        settings.addChangeListener(a::incrementAndGet);
        settings.addChangeListener(b::incrementAndGet);

        settings.setServerUrl("x");
        assertEquals(1, a.get());
        assertEquals(1, b.get());
    }

    /**
     * Simple in-memory Preferences implementation for testing.
     */
    private static class MapPreferences implements burp.api.montoya.persistence.Preferences {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
        private final Map<String, Integer> integers = new HashMap<>();

        @Override
        public String getString(String key) {
            return strings.get(key);
        }

        @Override
        public void setString(String key, String value) {
            strings.put(key, value);
        }

        @Override
        public Boolean getBoolean(String key) {
            return booleans.get(key);
        }

        @Override
        public void setBoolean(String key, boolean value) {
            booleans.put(key, value);
        }

        @Override
        public Integer getInteger(String key) {
            return integers.get(key);
        }

        @Override
        public void setInteger(String key, int value) {
            integers.put(key, value);
        }

        @Override
        public Byte getByte(String key) {
            return null;
        }

        @Override
        public void setByte(String key, byte value) {}

        @Override
        public Short getShort(String key) {
            return null;
        }

        @Override
        public void setShort(String key, short value) {}

        @Override
        public Long getLong(String key) {
            return null;
        }

        @Override
        public void setLong(String key, long value) {}

        @Override
        public void deleteString(String key) {
            strings.remove(key);
        }

        @Override
        public void deleteBoolean(String key) {
            booleans.remove(key);
        }

        @Override
        public void deleteByte(String key) {}

        @Override
        public void deleteShort(String key) {}

        @Override
        public void deleteInteger(String key) {
            integers.remove(key);
        }

        @Override
        public void deleteLong(String key) {}

        @Override
        public Set<String> stringKeys() {
            return strings.keySet();
        }

        @Override
        public Set<String> booleanKeys() {
            return booleans.keySet();
        }

        @Override
        public Set<String> byteKeys() {
            return Set.of();
        }

        @Override
        public Set<String> shortKeys() {
            return Set.of();
        }

        @Override
        public Set<String> integerKeys() {
            return integers.keySet();
        }

        @Override
        public Set<String> longKeys() {
            return Set.of();
        }
    }
}

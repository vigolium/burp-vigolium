package com.vigolium.extension.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Nested
    class SeverityTest {
        @Test
        void fromStringCritical() {
            assertEquals(Severity.CRITICAL, Severity.fromString("critical"));
            assertEquals(Severity.CRITICAL, Severity.fromString("CRITICAL"));
            assertEquals(Severity.CRITICAL, Severity.fromString("Critical"));
        }

        @Test
        void fromStringHigh() {
            assertEquals(Severity.HIGH, Severity.fromString("high"));
            assertEquals(Severity.HIGH, Severity.fromString("HIGH"));
        }

        @Test
        void fromStringMediumAndMedAlias() {
            assertEquals(Severity.MEDIUM, Severity.fromString("medium"));
            assertEquals(Severity.MEDIUM, Severity.fromString("med"));
            assertEquals(Severity.MEDIUM, Severity.fromString("MEDIUM"));
            assertEquals(Severity.MEDIUM, Severity.fromString("Med"));
        }

        @Test
        void fromStringLow() {
            assertEquals(Severity.LOW, Severity.fromString("low"));
            assertEquals(Severity.LOW, Severity.fromString("LOW"));
        }

        @Test
        void fromStringInfoAndUnknown() {
            assertEquals(Severity.INFO, Severity.fromString("info"));
            assertEquals(Severity.INFO, Severity.fromString("INFO"));
            assertEquals(Severity.INFO, Severity.fromString("unknown"));
            assertEquals(Severity.INFO, Severity.fromString(""));
        }

        @Test
        void fromStringNullReturnsInfo() {
            assertEquals(Severity.INFO, Severity.fromString(null));
        }

        @Test
        void allSeveritiesHaveLabelAndColor() {
            for (Severity s : Severity.values()) {
                assertNotNull(s.label());
                assertNotNull(s.color());
                assertFalse(s.label().isEmpty());
            }
        }

        @Test
        void sixEnumValues() {
            assertEquals(6, Severity.values().length);
        }

        @Test
        void labels() {
            assertEquals("Critical", Severity.CRITICAL.label());
            assertEquals("High", Severity.HIGH.label());
            assertEquals("Medium", Severity.MEDIUM.label());
            assertEquals("Low", Severity.LOW.label());
            assertEquals("Info", Severity.INFO.label());
        }
    }

    @Nested
    class FindingTest {
        @Test
        void recordFieldsMatchDesignDoc() {
            Finding f = new Finding(
                    1,
                    List.of("abc-123"),
                    "scan-456",
                    "xss-scanner",
                    "XSS Scanner",
                    "Reflected XSS via parameter 'q'",
                    Severity.HIGH,
                    "firm",
                    List.of("xss", "reflected"),
                    List.of("https://example.com/search?q=test"),
                    "2026-02-16T15:05:00Z",
                    "GET /search?q=test HTTP/1.1\r\nHost: example.com",
                    "HTTP/1.1 200 OK");

            assertEquals(1, f.id());
            assertEquals(List.of("abc-123"), f.httpRecordUuids());
            assertEquals("scan-456", f.scanUuid());
            assertEquals("xss-scanner", f.moduleId());
            assertEquals("XSS Scanner", f.moduleName());
            assertEquals("Reflected XSS via parameter 'q'", f.description());
            assertEquals(Severity.HIGH, f.severity());
            assertEquals("firm", f.confidence());
            assertEquals(List.of("xss", "reflected"), f.tags());
            assertEquals(List.of("https://example.com/search?q=test"), f.matchedAt());
            assertEquals("2026-02-16T15:05:00Z", f.foundAt());
        }
    }
}

package com.vigolium.extension.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterEngineTest {

    private FilterEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FilterEngine();
    }

    // --- Empty / Disabled rules ---

    @Test
    void emptyRulesAllowsAllRequests() {
        assertTrue(engine.evaluate(List.of(), mockRR("GET", "/test", "text/html", 200)));
    }

    @Test
    void allDisabledRulesAllowsAllRequests() {
        FilterRule r = new FilterRule(false, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "POST");
        assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
    }

    // --- FILE_EXTENSION ---

    @Nested
    class FileExtension {
        @Test
        void matchesExtension() {
            FilterRule r = rule(MatchType.FILE_EXTENSION, Relationship.MATCHES, "jpg|png|gif");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/image.jpg", "image/jpeg", 200)));
        }

        @Test
        void doesNotMatchExtension() {
            FilterRule r = rule(MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png|gif|css");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void doesNotMatchBlocksMatchingExtension() {
            FilterRule r = rule(MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png|gif|css");
            assertFalse(engine.evaluate(List.of(r), mockRR("GET", "/style.css", "text/css", 200)));
        }

        @Test
        void noExtensionDoesNotMatch() {
            FilterRule r = rule(MatchType.FILE_EXTENSION, Relationship.MATCHES, "jpg|png");
            assertFalse(engine.evaluate(List.of(r), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void noExtensionPassesDoesNotMatch() {
            FilterRule r = rule(MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/api/users", "application/json", 200)));
        }
    }

    // --- HTTP_METHOD ---

    @Nested
    class HttpMethod {
        @Test
        void matchesSingleMethod() {
            FilterRule r = rule(MatchType.HTTP_METHOD, Relationship.MATCHES, "GET");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }

        @Test
        void matchesPipeSeparatedMethods() {
            FilterRule r = rule(MatchType.HTTP_METHOD, Relationship.MATCHES, "GET|POST");
            assertTrue(engine.evaluate(List.of(r), mockRR("POST", "/test", "text/html", 200)));
        }

        @Test
        void doesNotMatchMethod() {
            FilterRule r = rule(MatchType.HTTP_METHOD, Relationship.DOES_NOT_MATCH, "DELETE");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }

        @Test
        void doesNotMatchBlocksMatchingMethod() {
            FilterRule r = rule(MatchType.HTTP_METHOD, Relationship.DOES_NOT_MATCH, "GET|POST");
            assertFalse(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }
    }

    // --- URL ---

    @Nested
    class Url {
        @Test
        void matchesRegex() {
            FilterRule r = rule(MatchType.URL, Relationship.MATCHES, "^https://api\\.target\\.com/.*");
            assertTrue(engine.evaluate(
                    List.of(r), mockRR("GET", "/users", "application/json", 200, "https://api.target.com/users")));
        }

        @Test
        void doesNotMatchRegex() {
            FilterRule r = rule(MatchType.URL, Relationship.DOES_NOT_MATCH, "^https://api\\.target\\.com/.*");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200, "https://other.com/test")));
        }

        @Test
        void doesNotMatchBlocksMatchingUrl() {
            FilterRule r = rule(MatchType.URL, Relationship.DOES_NOT_MATCH, "^https://api\\.target\\.com/.*");
            assertFalse(engine.evaluate(
                    List.of(r), mockRR("GET", "/users", "application/json", 200, "https://api.target.com/users")));
        }

        @Test
        void isInTargetScope() {
            FilterRule r = rule(MatchType.URL, Relationship.IS_IN_TARGET_SCOPE, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.isInScope()).thenReturn(true);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void isInTargetScopeReturnsFalseWhenOutOfScope() {
            FilterRule r = rule(MatchType.URL, Relationship.IS_IN_TARGET_SCOPE, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.isInScope()).thenReturn(false);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertFalse(engine.evaluate(List.of(r), rr));
        }

        @Test
        void isNotInTargetScope() {
            FilterRule r = rule(MatchType.URL, Relationship.IS_NOT_IN_TARGET_SCOPE, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.isInScope()).thenReturn(false);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }
    }

    // --- CONTENT_TYPE ---

    @Nested
    class ContentType {
        @Test
        void matchesContentType() {
            FilterRule r = rule(MatchType.CONTENT_TYPE, Relationship.MATCHES, "application/json");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/api", "application/json", 200)));
        }

        @Test
        void doesNotMatchContentType() {
            FilterRule r = rule(MatchType.CONTENT_TYPE, Relationship.DOES_NOT_MATCH, "image|video|font");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/api", "application/json", 200)));
        }

        @Test
        void doesNotMatchBlocksMatchingContentType() {
            FilterRule r = rule(MatchType.CONTENT_TYPE, Relationship.DOES_NOT_MATCH, "image|video|font");
            assertFalse(engine.evaluate(List.of(r), mockRR("GET", "/photo", "image/jpeg", 200)));
        }

        @Test
        void nullResponseWithDoesNotMatchReturnsTrue() {
            FilterRule r = rule(MatchType.CONTENT_TYPE, Relationship.DOES_NOT_MATCH, "image");
            HttpRequest request = mock(HttpRequest.class);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void nullResponseWithMatchesReturnsFalse() {
            FilterRule r = rule(MatchType.CONTENT_TYPE, Relationship.MATCHES, "application/json");
            HttpRequest request = mock(HttpRequest.class);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertFalse(engine.evaluate(List.of(r), rr));
        }
    }

    // --- STATUS_CODE ---

    @Nested
    class StatusCode {
        @Test
        void matchesStatusCode() {
            FilterRule r = rule(MatchType.STATUS_CODE, Relationship.MATCHES, "200|301|302");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }

        @Test
        void doesNotMatchStatusCode() {
            FilterRule r = rule(MatchType.STATUS_CODE, Relationship.DOES_NOT_MATCH, "404|500");
            assertTrue(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }

        @Test
        void doesNotMatchBlocksMatchingStatusCode() {
            FilterRule r = rule(MatchType.STATUS_CODE, Relationship.DOES_NOT_MATCH, "200");
            assertFalse(engine.evaluate(List.of(r), mockRR("GET", "/test", "text/html", 200)));
        }

        @Test
        void nullResponseWithDoesNotMatchReturnsTrue() {
            FilterRule r = rule(MatchType.STATUS_CODE, Relationship.DOES_NOT_MATCH, "200");
            HttpRequest request = mock(HttpRequest.class);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }
    }

    // --- HOST ---

    @Nested
    class Host {
        @Test
        void matchesHost() {
            FilterRule r = rule(MatchType.HOST, Relationship.MATCHES, ".*\\.target\\.com");
            HttpRequestResponse rr = mockRRWithHost("api.target.com");
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void doesNotMatchHost() {
            FilterRule r = rule(MatchType.HOST, Relationship.DOES_NOT_MATCH, ".*\\.target\\.com");
            HttpRequestResponse rr = mockRRWithHost("other.com");
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void doesNotMatchBlocksMatchingHost() {
            FilterRule r = rule(MatchType.HOST, Relationship.DOES_NOT_MATCH, ".*\\.target\\.com");
            HttpRequestResponse rr = mockRRWithHost("api.target.com");
            assertFalse(engine.evaluate(List.of(r), rr));
        }

        @Test
        void nullHostTreatedAsEmpty() {
            FilterRule r = rule(MatchType.HOST, Relationship.MATCHES, ".*\\.target\\.com");
            HttpRequest request = mock(HttpRequest.class);
            when(request.headerValue("Host")).thenReturn(null);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertFalse(engine.evaluate(List.of(r), rr));
        }
    }

    // --- REQUEST ---

    @Nested
    class Request {
        @Test
        void hasParameters() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.HAS_PARAMETERS, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.hasParameters()).thenReturn(true);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void doesNotHaveParameters() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.DOES_NOT_HAVE_PARAMETERS, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.hasParameters()).thenReturn(false);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void hasBody() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.HAS_BODY, "");
            HttpRequest request = mock(HttpRequest.class);
            ByteArray body = mock(ByteArray.class);
            when(body.length()).thenReturn(10);
            when(request.body()).thenReturn(body);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void doesNotHaveBody() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.DOES_NOT_HAVE_BODY, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.body()).thenReturn(null);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertTrue(engine.evaluate(List.of(r), rr));
        }

        @Test
        void hasBodyFalseWhenNoBody() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.HAS_BODY, "");
            HttpRequest request = mock(HttpRequest.class);
            when(request.body()).thenReturn(null);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertFalse(engine.evaluate(List.of(r), rr));
        }

        @Test
        void hasBodyFalseWhenEmptyBody() {
            FilterRule r = rule(MatchType.REQUEST, Relationship.HAS_BODY, "");
            HttpRequest request = mock(HttpRequest.class);
            ByteArray body = mock(ByteArray.class);
            when(body.length()).thenReturn(0);
            when(request.body()).thenReturn(body);
            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(null);
            assertFalse(engine.evaluate(List.of(r), rr));
        }
    }

    // --- Operator Logic ---

    @Nested
    class OperatorLogic {
        @Test
        void orOperatorTrueWhenAnyRuleMatches() {
            FilterRule r1 = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "POST");
            FilterRule r2 =
                    new FilterRule(true, Operator.OR, MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png");

            // r1: GET!=POST → false, r2: no ext → true, false OR true → true
            assertTrue(engine.evaluate(List.of(r1, r2), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void orOperatorFalseWhenNoRuleMatches() {
            FilterRule r1 = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "DELETE");
            FilterRule r2 =
                    new FilterRule(true, Operator.OR, MatchType.FILE_EXTENSION, Relationship.MATCHES, "jpg|png");

            // r1: GET!=DELETE → false, r2: no ext → false, false OR false → false
            assertFalse(engine.evaluate(List.of(r1, r2), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void andOperatorTrueWhenAllRulesMatch() {
            FilterRule r1 = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "GET");
            FilterRule r2 = new FilterRule(
                    true, Operator.AND, MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png");

            // r1: true, r2: true, true AND true → true
            assertTrue(engine.evaluate(List.of(r1, r2), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void andOperatorFalseWhenOneRuleFails() {
            FilterRule r1 = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "POST");
            FilterRule r2 = new FilterRule(
                    true, Operator.AND, MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "jpg|png");

            // r1: GET!=POST → false, false AND true → false
            assertFalse(engine.evaluate(List.of(r1, r2), mockRR("GET", "/api/users", "application/json", 200)));
        }

        @Test
        void mixedOperatorsEvaluateLeftToRight() {
            // A AND B OR C → (A AND B) OR C (left to right, no precedence)
            FilterRule r1 = new FilterRule(true, null, MatchType.HTTP_METHOD, Relationship.MATCHES, "GET");
            FilterRule r2 = new FilterRule(
                    true, Operator.AND, MatchType.FILE_EXTENSION, Relationship.MATCHES, "css"); // false for /api/users
            FilterRule r3 =
                    new FilterRule(true, Operator.OR, MatchType.CONTENT_TYPE, Relationship.MATCHES, "application/json");

            // r1: true, r2: false, true AND false = false
            // false OR r3(true) = true
            assertTrue(engine.evaluate(List.of(r1, r2, r3), mockRR("GET", "/api/users", "application/json", 200)));
        }
    }

    // --- Default Rules from Design Doc ---

    @Nested
    class DefaultRules {

        private List<FilterRule> defaultRules() {
            return FilterRule.getDefaultRules();
        }

        @Test
        void apiRequestPassesAllRules() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.method()).thenReturn("POST");
            when(request.pathWithoutQuery()).thenReturn("/api/users");

            HttpResponse response = mock(HttpResponse.class);

            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(response);

            // rule1: no ext → true, AND rule2: POST != OPTIONS|HEAD → true
            assertTrue(engine.evaluate(defaultRules(), rr));
        }

        @Test
        void jpgImageBlockedByExtension() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.method()).thenReturn("GET");
            when(request.pathWithoutQuery()).thenReturn("/photo.jpg");

            HttpResponse response = mock(HttpResponse.class);

            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(response);

            // rule1: jpg matches ext filter → false
            assertFalse(engine.evaluate(defaultRules(), rr));
        }

        @Test
        void optionsRequestBlockedByMethodFilter() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.method()).thenReturn("OPTIONS");
            when(request.pathWithoutQuery()).thenReturn("/api/users");

            HttpResponse response = mock(HttpResponse.class);

            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(response);

            // rule1: no ext → true, AND rule2: OPTIONS matches → false
            assertFalse(engine.evaluate(defaultRules(), rr));
        }

        @Test
        void headRequestBlockedByMethodFilter() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.method()).thenReturn("HEAD");
            when(request.pathWithoutQuery()).thenReturn("/api/users");

            HttpResponse response = mock(HttpResponse.class);

            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(response);

            // rule1: no ext → true, AND rule2: HEAD matches → false
            assertFalse(engine.evaluate(defaultRules(), rr));
        }

        @Test
        void normalRequestPassesWithoutScopeRule() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.method()).thenReturn("GET");
            when(request.pathWithoutQuery()).thenReturn("/api/data");

            HttpResponse response = mock(HttpResponse.class);

            HttpRequestResponse rr = mock(HttpRequestResponse.class);
            when(rr.request()).thenReturn(request);
            when(rr.response()).thenReturn(response);

            // rule1: no ext → true, AND rule2: GET != OPTIONS|HEAD → true
            assertTrue(engine.evaluate(defaultRules(), rr));
        }
    }

    // --- FilterRule copy ---

    @Test
    void filterRuleCopyCreatesIndependentCopy() {
        FilterRule original = new FilterRule(true, Operator.OR, MatchType.HOST, Relationship.MATCHES, "example.com");
        FilterRule copy = original.copy();

        copy.setEnabled(false);
        copy.setOperator(Operator.AND);
        copy.setMatchType(MatchType.URL);
        copy.setRelationship(Relationship.DOES_NOT_MATCH);
        copy.setCondition("other.com");

        assertTrue(original.isEnabled());
        assertEquals(Operator.OR, original.getOperator());
        assertEquals(MatchType.HOST, original.getMatchType());
        assertEquals(Relationship.MATCHES, original.getRelationship());
        assertEquals("example.com", original.getCondition());
    }

    // --- Helper methods ---

    private static FilterRule rule(MatchType matchType, Relationship relationship, String condition) {
        return new FilterRule(true, null, matchType, relationship, condition);
    }

    private HttpRequestResponse mockRR(String method, String path, String contentType, int statusCode) {
        return mockRR(method, path, contentType, statusCode, "https://example.com" + path);
    }

    private HttpRequestResponse mockRR(String method, String path, String contentType, int statusCode, String url) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn(method);
        when(request.pathWithoutQuery()).thenReturn(path);
        when(request.url()).thenReturn(url);
        when(request.headerValue("Host")).thenReturn("example.com");
        when(request.hasParameters()).thenReturn(false);
        when(request.body()).thenReturn(null);

        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(Short.valueOf((short) statusCode));
        when(response.headerValue("Content-Type")).thenReturn(contentType);

        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        when(rr.request()).thenReturn(request);
        when(rr.response()).thenReturn(response);

        return rr;
    }

    private HttpRequestResponse mockRRWithHost(String host) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.headerValue("Host")).thenReturn(host);
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        when(rr.request()).thenReturn(request);
        when(rr.response()).thenReturn(null);
        return rr;
    }
}

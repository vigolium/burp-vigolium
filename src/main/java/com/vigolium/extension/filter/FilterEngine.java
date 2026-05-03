package com.vigolium.extension.filter;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.List;
import java.util.regex.Pattern;

public class FilterEngine {

    public boolean evaluate(List<FilterRule> rules, HttpRequestResponse requestResponse) {
        List<FilterRule> enabledRules =
                rules.stream().filter(FilterRule::isEnabled).toList();

        if (enabledRules.isEmpty()) {
            return true;
        }

        boolean result = evaluateRule(enabledRules.get(0), requestResponse);

        for (int i = 1; i < enabledRules.size(); i++) {
            FilterRule rule = enabledRules.get(i);
            boolean ruleResult = evaluateRule(rule, requestResponse);

            if (rule.getOperator() == Operator.AND) {
                result = result && ruleResult;
            } else {
                result = result || ruleResult;
            }
        }

        return result;
    }

    private boolean evaluateRule(FilterRule rule, HttpRequestResponse requestResponse) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();

        return switch (rule.getMatchType()) {
            case FILE_EXTENSION -> evaluateFileExtension(rule, request);
            case HTTP_METHOD -> evaluateHttpMethod(rule, request);
            case URL -> evaluateUrl(rule, request);
            case CONTENT_TYPE -> evaluateContentType(rule, response);
            case STATUS_CODE -> evaluateStatusCode(rule, response);
            case HOST -> evaluateHost(rule, request);
            case REQUEST -> evaluateRequest(rule, request);
        };
    }

    private boolean evaluateFileExtension(FilterRule rule, HttpRequest request) {
        String path = request.pathWithoutQuery();
        String extension = "";
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < path.length() - 1) {
            extension = path.substring(dotIndex + 1).toLowerCase();
        }
        return evaluateStringMatch(rule.getRelationship(), rule.getCondition(), extension, true);
    }

    private boolean evaluateHttpMethod(FilterRule rule, HttpRequest request) {
        return evaluateStringMatch(rule.getRelationship(), rule.getCondition(), request.method(), true);
    }

    private boolean evaluateUrl(FilterRule rule, HttpRequest request) {
        return switch (rule.getRelationship()) {
            case IS_IN_TARGET_SCOPE -> request.isInScope();
            case IS_NOT_IN_TARGET_SCOPE -> !request.isInScope();
            default -> evaluateStringMatch(rule.getRelationship(), rule.getCondition(), request.url(), false);
        };
    }

    private boolean evaluateContentType(FilterRule rule, HttpResponse response) {
        if (response == null) {
            return rule.getRelationship() == Relationship.DOES_NOT_MATCH
                    || rule.getRelationship() == Relationship.NOT_EQUALS;
        }
        String contentType = response.headerValue("Content-Type");
        if (contentType == null) contentType = "";
        return switch (rule.getRelationship()) {
            case MATCHES -> Pattern.compile(rule.getCondition(), Pattern.CASE_INSENSITIVE)
                    .matcher(contentType)
                    .find();
            case DOES_NOT_MATCH -> !Pattern.compile(rule.getCondition(), Pattern.CASE_INSENSITIVE)
                    .matcher(contentType)
                    .find();
            case EQUALS -> contentType.equalsIgnoreCase(rule.getCondition());
            case NOT_EQUALS -> !contentType.equalsIgnoreCase(rule.getCondition());
            default -> false;
        };
    }

    private boolean evaluateStatusCode(FilterRule rule, HttpResponse response) {
        if (response == null) {
            return rule.getRelationship() == Relationship.DOES_NOT_MATCH
                    || rule.getRelationship() == Relationship.NOT_EQUALS;
        }
        String statusCode = String.valueOf(response.statusCode());
        return evaluateStringMatch(rule.getRelationship(), rule.getCondition(), statusCode, false);
    }

    private boolean evaluateHost(FilterRule rule, HttpRequest request) {
        String host = request.headerValue("Host");
        if (host == null) host = "";
        return evaluateStringMatch(rule.getRelationship(), rule.getCondition(), host, true);
    }

    private boolean evaluateRequest(FilterRule rule, HttpRequest request) {
        return switch (rule.getRelationship()) {
            case HAS_PARAMETERS -> request.hasParameters();
            case DOES_NOT_HAVE_PARAMETERS -> !request.hasParameters();
            case HAS_BODY -> request.body() != null && request.body().length() > 0;
            case DOES_NOT_HAVE_BODY -> request.body() == null || request.body().length() == 0;
            default -> false;
        };
    }

    private boolean evaluateStringMatch(
            Relationship relationship, String condition, String value, boolean caseInsensitive) {
        return switch (relationship) {
            case MATCHES -> {
                int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
                yield Pattern.compile(condition, flags).matcher(value).matches();
            }
            case DOES_NOT_MATCH -> {
                int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
                yield !Pattern.compile(condition, flags).matcher(value).matches();
            }
            case EQUALS -> caseInsensitive ? value.equalsIgnoreCase(condition) : value.equals(condition);
            case NOT_EQUALS -> caseInsensitive ? !value.equalsIgnoreCase(condition) : !value.equals(condition);
            default -> false;
        };
    }
}

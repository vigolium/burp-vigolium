package com.vigolium.extension.filter;

import java.util.ArrayList;
import java.util.List;

public class FilterRule {
    private boolean enabled;
    private Operator operator;
    private MatchType matchType;
    private Relationship relationship;
    private String condition;

    public FilterRule(
            boolean enabled, Operator operator, MatchType matchType, Relationship relationship, String condition) {
        this.enabled = enabled;
        this.operator = operator;
        this.matchType = matchType;
        this.relationship = relationship;
        this.condition = condition;
    }

    public FilterRule copy() {
        return new FilterRule(enabled, operator, matchType, relationship, condition);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public static List<FilterRule> getDefaultRules() {
        return new ArrayList<>(List.of(
                new FilterRule(
                        true,
                        null,
                        MatchType.FILE_EXTENSION,
                        Relationship.DOES_NOT_MATCH,
                        "woff|woff2|ttf|eot|otf"
                                + "|mp4|webm|ogg|mkv|flv|avi|mov|wmv|m3u8"
                                + "|svg|jpg|jpeg|png|gif|bmp|webp|ico"
                                + "|css|js"
                                + "|pdf|zip|exe|gz|rar"
                                + "|mp3"),
                new FilterRule(
                        true, Operator.AND, MatchType.HTTP_METHOD, Relationship.DOES_NOT_MATCH, "OPTIONS|HEAD")));
    }
}

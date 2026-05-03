package com.vigolium.extension.filter;

public enum Relationship {
    MATCHES("Matches", true),
    DOES_NOT_MATCH("Does not match", true),
    EQUALS("Equals", true),
    NOT_EQUALS("Not equals", true),
    IS_IN_TARGET_SCOPE("Is in target scope", false),
    IS_NOT_IN_TARGET_SCOPE("Is not in target scope", false),
    HAS_PARAMETERS("Has parameters", false),
    HAS_BODY("Has body", false),
    DOES_NOT_HAVE_PARAMETERS("Does not have parameters", false),
    DOES_NOT_HAVE_BODY("Does not have body", false);

    private final String label;
    private final boolean needsCondition;

    Relationship(String label, boolean needsCondition) {
        this.label = label;
        this.needsCondition = needsCondition;
    }

    public String label() {
        return label;
    }

    public boolean needsCondition() {
        return needsCondition;
    }

    @Override
    public String toString() {
        return label;
    }
}

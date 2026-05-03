package com.vigolium.extension.filter;

public enum Operator {
    AND("And"),
    OR("Or");

    private final String label;

    Operator(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

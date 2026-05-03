package com.vigolium.extension.model;

import java.awt.Color;
import java.util.Comparator;
import javax.swing.UIManager;

public enum Severity {
    CRITICAL("Critical", "Colors.ui.issue.high"),
    HIGH("High", "Colors.ui.issue.high"),
    MEDIUM("Medium", "Colors.ui.issue.medium"),
    LOW("Low", "Colors.ui.issue.low"),
    SUSPECT("Suspect", "Colors.ui.issue.info"),
    INFO("Info", "Colors.ui.issue.info");

    private final String label;
    private final String colorKey;

    Severity(String label, String colorKey) {
        this.label = label;
        this.colorKey = colorKey;
    }

    public String label() {
        return label;
    }

    public Color color() {
        Color c = UIManager.getColor(colorKey);
        return c != null ? c : Color.GRAY;
    }

    public static final Comparator<Severity> BY_ORDINAL = Comparator.comparingInt(Enum::ordinal);

    public static Severity fromString(String value) {
        if (value == null) return INFO;
        return switch (value.toLowerCase()) {
            case "critical" -> CRITICAL;
            case "high" -> HIGH;
            case "medium", "med" -> MEDIUM;
            case "low" -> LOW;
            case "suspect" -> SUSPECT;
            default -> INFO;
        };
    }
}

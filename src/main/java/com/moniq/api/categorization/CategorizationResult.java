package com.moniq.api.categorization;

import java.math.BigDecimal;

public class CategorizationResult {

    private final String category;
    private final String subcategory;
    private final BigDecimal confidence;
    private final String source;

    public CategorizationResult(String category, BigDecimal confidence) {
        this(category, "Unknown", confidence, "RULE");
    }

    public CategorizationResult(String category, String subcategory, BigDecimal confidence, String source) {
        this.category = normalizeCategory(category);
        this.subcategory = normalizeSubcategory(subcategory);
        this.confidence = confidence == null ? new BigDecimal("0.30") : confidence;
        this.source = normalizeSource(source);
    }

    public static CategorizationResult unknown() {
        return new CategorizationResult("OTHER", "Unknown", new BigDecimal("0.30"), "UNKNOWN");
    }

    public String getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }

    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER";
        }
        return value.trim();
    }

    private String normalizeSubcategory(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.trim();
    }

    private String normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().toUpperCase();
    }
}
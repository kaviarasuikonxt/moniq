// src/main/java/com/moniq/api/categorization/CategorizationResult.java
package com.moniq.api.categorization;

public class CategorizationResult {
    private final String category;
    private final double confidence;

    public CategorizationResult(String category, double confidence) {
        this.category = category;
        this.confidence = confidence;
    }

    public String getCategory() { return category; }
    public double getConfidence() { return confidence; }
}
// src/main/java/com/moniq/api/categorization/CategorizationResult.java
package com.moniq.api.categorization;

import java.math.BigDecimal;

public class CategorizationResult {
    private final String category;
    private final BigDecimal  confidence;

    public CategorizationResult(String category, BigDecimal  confidence) {
        this.category = category;
        this.confidence = confidence;
    }

    public String getCategory() { return category; }
    public BigDecimal  getConfidence() { return confidence; }
}
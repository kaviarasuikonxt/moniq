package com.moniq.api.categorization;

import java.math.BigDecimal;

public enum CategoryConfidence {

    EXACT_MATCH(new BigDecimal("0.92")),
    KEYWORD_MATCH(new BigDecimal("0.80")),
    PARTIAL_MATCH(new BigDecimal("0.65")),
    UNKNOWN(new BigDecimal("0.30"));

    private final BigDecimal score;

    CategoryConfidence(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getScore() {
        return score;
    }
}
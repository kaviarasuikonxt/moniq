// src/main/java/com/moniq/api/categorization/AiCategorizer.java
package com.moniq.api.categorization;

public interface AiCategorizer {
    CategorizationResult categorize(String itemName, String rawLine);
}
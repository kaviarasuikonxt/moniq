// src/main/java/com/moniq/api/categorization/RuleBasedCategorizer.java
package com.moniq.api.categorization;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class RuleBasedCategorizer implements AiCategorizer {

    private static final Map<String, List<String>> KEYWORDS = Map.of(
        "DAIRY", List.of("milk", "cheese", "yogurt", "butter", "cream"),
        "MEAT_SEAFOOD", List.of("chicken", "beef", "pork", "fish", "salmon", "prawn", "shrimp", "lamb"),
        "PRODUCE", List.of("apple", "banana", "orange", "tomato", "onion", "potato", "lettuce", "spinach", "carrot", "broccoli"),
        "BEVERAGES", List.of("coke", "cola", "sprite", "tea", "coffee", "juice", "water", "beer", "wine"),
        "SNACKS", List.of("chips", "cookie", "cookies", "biscuit", "chocolate", "candy", "cracker"),
        "HOUSEHOLD", List.of("detergent", "tissue", "toilet", "bleach", "soap", "dish", "sponge", "trash", "garbage", "bag"),
        "PERSONAL_CARE", List.of("shampoo", "conditioner", "toothpaste", "toothbrush", "deodorant", "lotion", "skincare"),
        "BABY", List.of("diaper", "nappy", "formula", "baby"),
        "PHARMACY", List.of("panadol", "paracetamol", "vitamin", "supplement", "bandage"),
        "GROCERIES", List.of("rice", "noodle", "noodles", "bread", "egg", "eggs", "flour", "sugar", "salt", "oil")
    );

    @Override
    public CategorizationResult categorize(String itemName, String rawLine) {
        String text = ((itemName != null && !itemName.isBlank()) ? itemName : rawLine);
        if (text == null) return new CategorizationResult("OTHER", new BigDecimal("0.30"));

        String t = text.toLowerCase(Locale.ROOT);

        String bestCategory = "OTHER";
        int bestScore = 0;

        for (var entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (t.contains(kw)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        if (bestScore <= 0) return new CategorizationResult("OTHER", new BigDecimal("0.30"));

        // Simple confidence curve: 1 match => 0.60, 2 => 0.75, 3 => 0.85, >=4 => 0.92
        double confidence = switch (bestScore) {
            case 1 -> 0.60;
            case 2 -> 0.75;
            case 3 -> 0.85;
            default -> 0.92;
        };
        return new CategorizationResult(bestCategory, new BigDecimal(String.valueOf(confidence)));
    }
}
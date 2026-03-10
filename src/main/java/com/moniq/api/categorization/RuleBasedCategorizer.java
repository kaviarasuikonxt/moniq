package com.moniq.api.categorization;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RuleBasedCategorizer {

    private static final Map<String, CategoryRule> KEYWORDS = Map.ofEntries(
            Map.entry("DAIRY", new CategoryRule("GROCERIES", "DAIRY",
                    List.of("milk", "cheese", "yogurt", "butter", "cream", "egg", "eggs", "yoghurt"))),

            Map.entry("MEAT_SEAFOOD", new CategoryRule("GROCERIES", "MEAT_SEAFOOD",
                    List.of("chicken", "beef", "pork", "fish", "salmon", "prawn", "shrimp", "lamb", "seafood"))),

            Map.entry("PRODUCE_FRUITS", new CategoryRule("GROCERIES", "FRUITS",
                    List.of("apple", "banana", "orange", "guava", "mango", "pear", "grape", "papaya"))),

            Map.entry("PRODUCE_VEGETABLES", new CategoryRule("GROCERIES", "VEGETABLES",
                    List.of("tomato", "onion", "potato", "lettuce", "spinach", "carrot", "broccoli", "lemon", "cauliflower", "cali flower"))),

            Map.entry("BEVERAGES", new CategoryRule("FOOD_BEVERAGE", "DRINKS",
                    List.of("coke", "cola", "sprite", "tea", "coffee", "juice", "water", "beer", "wine", "milo", "kopi", "teh"))),

            Map.entry("SNACKS", new CategoryRule("GROCERIES", "SNACKS",
                    List.of("chips", "cookie", "cookies", "biscuit", "chocolate", "candy", "cracker", "wafer"))),

            Map.entry("HOUSEHOLD", new CategoryRule("RETAIL", "HOUSEHOLD",
                    List.of("detergent", "tissue", "toilet", "bleach", "soap", "dish", "sponge", "trash", "garbage", "bag", "cleaner"))),

            Map.entry("PERSONAL_CARE", new CategoryRule("HEALTH", "PERSONAL_CARE",
                    List.of("shampoo", "conditioner", "toothpaste", "toothbrush", "deodorant", "lotion", "skincare", "body wash", "face wash"))),

            Map.entry("BABY", new CategoryRule("HEALTH", "BABY",
                    List.of("diaper", "nappy", "formula", "baby", "wipes", "infant"))),

            Map.entry("PHARMACY", new CategoryRule("HEALTH", "MEDICINE",
                    List.of("panadol", "paracetamol", "vitamin", "supplement", "bandage", "medicine", "tablet", "capsule", "ointment"))),

            Map.entry("BAKERY", new CategoryRule("GROCERIES", "BAKERY",
                    List.of("bread", "bun", "cake", "croissant", "pastry", "bagel", "muffin", "toast", "loaf", "roll", "donut", "doughnut", "pain", "chocolat", "roti"))),

            Map.entry("PANTRY", new CategoryRule("GROCERIES", "PANTRY",
                    List.of("rice", "noodle", "noodles", "flour", "sugar", "salt", "oil", "kaya", "quinoa", "basmathi", "basmati", "ponni", "peanut")))
    );

    public CategorizationResult categorize(String itemName, String rawLine) {
        String text = buildComparableText(itemName, rawLine);
        if (text.isBlank()) {
            return CategorizationResult.unknown();
        }

        CategoryRule bestRule = null;
        int bestScore = 0;

        for (CategoryRule rule : KEYWORDS.values()) {
            int score = 0;
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestRule = rule;
            }
        }

        if (bestRule == null || bestScore <= 0) {
            return CategorizationResult.unknown();
        }

        BigDecimal confidence = switch (bestScore) {
            case 1 -> new BigDecimal("0.60");
            case 2 -> new BigDecimal("0.75");
            case 3 -> new BigDecimal("0.85");
            default -> new BigDecimal("0.92");
        };

        return new CategorizationResult(
                bestRule.category(),
                bestRule.subcategory(),
                confidence,
                "RULE"
        );
    }

    private String buildComparableText(String itemName, String rawLine) {
        String preferred = (itemName != null && !itemName.isBlank()) ? itemName : rawLine;
        String fallback = (rawLine == null) ? "" : rawLine;
        String combined = ((preferred == null ? "" : preferred) + " " + fallback)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return combined;
    }

    private record CategoryRule(String category, String subcategory, List<String> keywords) {
    }
}
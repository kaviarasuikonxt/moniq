package com.moniq.api.categorization;

import com.moniq.api.web.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ReceiptCategoryEngine {

    private static final Logger log = LoggerFactory.getLogger(ReceiptCategoryEngine.class);

    private final Map<String, Set<String>> categoryKeywords = new LinkedHashMap<>();

    public ReceiptCategoryEngine() {

        categoryKeywords.put("DAIRY", Set.of(
                "milk", "cheese", "yogurt", "butter", "cream", "egg", "eggs"
        ));

        categoryKeywords.put("MEAT_SEAFOOD", Set.of(
                "chicken", "beef", "fish", "salmon", "prawn", "pork", "mutton", "seafood"
        ));

        categoryKeywords.put("PRODUCE", Set.of(
                "apple", "banana", "onion", "potato", "spinach", "tomato",
                "vegetable", "veg", "fruit", "lettuce", "carrot", "broccoli"
        ));

        categoryKeywords.put("BEVERAGES", Set.of(
                "juice", "tea", "coffee", "cola", "water", "drink", "soda", "milk tea"
        ));

        categoryKeywords.put("SNACKS", Set.of(
                "chips", "biscuit", "cookie", "chocolate", "snack", "cracker", "wafer"
        ));

        categoryKeywords.put("BAKERY", Set.of(
                "bread", "bun", "cake", "croissant", "pastry", "bagel", "muffin",
                "toast", "loaf", "roll", "donut", "doughnut", "pain", "chocolat"
        ));

        categoryKeywords.put("HOUSEHOLD", Set.of(
                "detergent", "tissue", "cleaner", "soap", "sponge", "napkin"
        ));

        categoryKeywords.put("PERSONAL_CARE", Set.of(
                "shampoo", "conditioner", "toothpaste", "lotion", "body wash", "face wash"
        ));

        categoryKeywords.put("BABY", Set.of(
                "diaper", "formula", "baby", "wipes", "infant"
        ));

        categoryKeywords.put("PHARMACY", Set.of(
                "medicine", "vitamin", "tablet", "capsule", "panadol", "ointment"
        ));
    }

    public CategorizationResult categorize(String itemName) {
        return categorize(itemName, itemName);
    }

    public CategorizationResult categorize(String itemName, String rawLine) {

        String name = safe(itemName);
        String raw = safe(rawLine);
        String combined = (name + " " + raw).trim();

        if (combined.isBlank()) {
            return new CategorizationResult("OTHER", CategoryConfidence.UNKNOWN.getScore());
        }

        for (Map.Entry<String, Set<String>> entry : categoryKeywords.entrySet()) {
            String category = entry.getKey();

            for (String keyword : entry.getValue()) {
                String kw = keyword.toLowerCase();

                if (name.equals(kw)) {
                    log.info("[{}] Category exact match item={} category={}",
                            RequestCorrelation.getRequestId(), itemName, category);
                    return new CategorizationResult(category, CategoryConfidence.EXACT_MATCH.getScore());
                }

                if (containsWholeWord(name, kw) || containsWholeWord(combined, kw)) {
                    log.info("[{}] Category keyword match item={} category={} keyword={}",
                            RequestCorrelation.getRequestId(), itemName, category, kw);
                    return new CategorizationResult(category, CategoryConfidence.KEYWORD_MATCH.getScore());
                }

                if (name.contains(kw) || combined.contains(kw)) {
                    log.info("[{}] Category partial match item={} category={} keyword={}",
                            RequestCorrelation.getRequestId(), itemName, category, kw);
                    return new CategorizationResult(category, CategoryConfidence.PARTIAL_MATCH.getScore());
                }
            }
        }

        log.info("[{}] Category unknown item={}",
                RequestCorrelation.getRequestId(), itemName);

        return new CategorizationResult("OTHER", CategoryConfidence.UNKNOWN.getScore());
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsWholeWord(String text, String keyword) {
        return text.matches(".*\\b" + java.util.regex.Pattern.quote(keyword) + "\\b.*");
    }
}
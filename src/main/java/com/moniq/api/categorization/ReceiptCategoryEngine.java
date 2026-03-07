package com.moniq.api.categorization;

import com.moniq.api.web.RequestCorrelation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ReceiptCategoryEngine {

    private final Map<String, Set<String>> categoryKeywords = new HashMap<>();

    public ReceiptCategoryEngine() {

        categoryKeywords.put("DAIRY", Set.of(
                "milk","cheese","yogurt","butter","cream"
        ));

        categoryKeywords.put("MEAT_SEAFOOD", Set.of(
                "chicken","beef","fish","salmon","prawn","pork"
        ));

        categoryKeywords.put("PRODUCE", Set.of(
                "apple","banana","onion","potato","spinach","tomato"
        ));

        categoryKeywords.put("BEVERAGES", Set.of(
                "juice","tea","coffee","cola","water","drink"
        ));

        categoryKeywords.put("SNACKS", Set.of(
                "chips","biscuit","cookie","chocolate","snack"
        ));

        categoryKeywords.put("BAKERY", Set.of(
                "bread","bun","cake","croissant"
        ));

        categoryKeywords.put("HOUSEHOLD", Set.of(
                "detergent","tissue","cleaner","soap"
        ));

        categoryKeywords.put("PERSONAL_CARE", Set.of(
                "shampoo","conditioner","toothpaste","lotion"
        ));

        categoryKeywords.put("BABY", Set.of(
                "diaper","formula","baby"
        ));

        categoryKeywords.put("PHARMACY", Set.of(
                "medicine","vitamin","tablet"
        ));
    }

    public CategorizationResult categorize(String itemName) {

        if (itemName == null || itemName.isBlank()) {
            return new CategorizationResult(
                    "OTHER",
                    CategoryConfidence.UNKNOWN.getScore()
            );
        }

        String name = itemName.toLowerCase();

        for (Map.Entry<String, Set<String>> entry : categoryKeywords.entrySet()) {

            String category = entry.getKey();
            Set<String> keywords = entry.getValue();

            for (String keyword : keywords) {

                if (name.equals(keyword)) {

                    log.info("[{}] Category exact match item={} category={}",
                            RequestCorrelation.getRequestId(),
                            itemName,
                            category);

                    return new CategorizationResult(
                            category,
                            CategoryConfidence.EXACT_MATCH.getScore()
                    );
                }

                if (name.contains(keyword)) {

                    log.info("[{}] Category keyword match item={} category={}",
                            RequestCorrelation.getRequestId(),
                            itemName,
                            category);

                    return new CategorizationResult(
                            category,
                            CategoryConfidence.KEYWORD_MATCH.getScore()
                    );
                }
            }
        }

        log.info("[{}] Category unknown item={}",
                RequestCorrelation.getRequestId(),
                itemName);

        return new CategorizationResult(
                "OTHER",
                CategoryConfidence.UNKNOWN.getScore()
        );
    }
}
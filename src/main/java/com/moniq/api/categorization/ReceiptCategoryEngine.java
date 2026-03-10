package com.moniq.api.categorization;

import com.moniq.api.web.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ReceiptCategoryEngine implements AiCategorizer {

    private static final Logger log = LoggerFactory.getLogger(ReceiptCategoryEngine.class);

    private final RuleBasedCategorizer ruleBasedCategorizer;
    private final boolean aiEnabled;

    public ReceiptCategoryEngine(
            RuleBasedCategorizer ruleBasedCategorizer,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.ruleBasedCategorizer = ruleBasedCategorizer;
        this.aiEnabled = aiEnabled;
    }

    public CategorizationResult categorize(String itemName) {
        return categorize(itemName, itemName);
    }

    @Override
    public CategorizationResult categorize(String itemName, String rawLine) {
        CategorizationResult ruleResult = ruleBasedCategorizer.categorize(itemName, rawLine);

        if (!"OTHER".equalsIgnoreCase(ruleResult.getCategory())) {
            log.info("[{}] Category resolved item={} category={} subcategory={} source={}",
                    RequestCorrelation.getRequestId(),
                    safe(itemName),
                    ruleResult.getCategory(),
                    ruleResult.getSubcategory(),
                    ruleResult.getSource());

            return ruleResult;
        }

        if (aiEnabled) {
            CategorizationResult aiFallback = new CategorizationResult(
                    "OTHER",
                    "Unknown",
                    new BigDecimal("0.55"),
                    "AI"
            );

            log.info("[{}] Category fallback item={} category={} subcategory={} source={}",
                    RequestCorrelation.getRequestId(),
                    safe(itemName),
                    aiFallback.getCategory(),
                    aiFallback.getSubcategory(),
                    aiFallback.getSource());

            return aiFallback;
        }

        log.info("[{}] Category unknown item={}",
                RequestCorrelation.getRequestId(),
                safe(itemName));

        return ruleResult;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
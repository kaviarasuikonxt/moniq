package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class RestaurantParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[-$]?\\d+(\\.\\d{1,3})?$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.RESTAURANT;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String layoutPreferred = filterRestaurantSection(input.getLayoutText());
        String rawFallback = filterRestaurantSection(input.getRawText());

        if (score(layoutPreferred) >= score(rawFallback) && !layoutPreferred.isBlank()) {
            return layoutPreferred;
        }

        if (!rawFallback.isBlank()) {
            return rawFallback;
        }

        return input.getRawText();
    }

    private String filterRestaurantSection(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        boolean started = false;

        for (String line : lines) {
            String value = normalize(line);
            if (value.isBlank()) {
                continue;
            }

            String upper = value.toUpperCase();

            if (!started) {
                if (looksLikeRestaurantItemStart(upper)) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (isFooterStart(upper)) {
                break;
            }

            if (shouldSkip(upper, value)) {
                continue;
            }

            result.add(cleanLine(value));
        }

        return String.join("\n", result).trim();
    }

    private boolean looksLikeRestaurantItemStart(String upper) {
        return upper.contains("ITEM")
                || upper.contains("DESCRIPTION")
                || upper.contains("QTY")
                || upper.contains("PRICE")
                || upper.contains("AMOUNT")
                || upper.contains("MENU")
                || upper.contains("SET MEAL")
                || upper.contains("COMBO")
                || upper.contains("TAKEAWAY")
                || upper.contains("DINE IN")
                || upper.contains("PAX");
    }

    private boolean shouldSkip(String upper, String value) {
        if (value.isBlank()) {
            return true;
        }

        if (DECORATOR_PATTERN.matcher(value).matches()) {
            return true;
        }

        return upper.equals("ITEM")
                || upper.equals("DESCRIPTION")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("AMOUNT")
                || upper.equals("TOTAL")
                || upper.startsWith("TABLE")
                || upper.startsWith("PAX")
                || upper.startsWith("ORDER")
                || upper.startsWith("QUEUE")
                || upper.startsWith("CASHIER")
                || upper.startsWith("DATE")
                || upper.startsWith("TIME")
                || upper.startsWith("RECEIPT")
                || upper.startsWith("STORE")
                || upper.startsWith("OUTLET")
                || upper.startsWith("GST")
                || upper.startsWith("SERVICE CHARGE");
    }

    private boolean isFooterStart(String upper) {
        return upper.startsWith("SUBTOTAL")
                || upper.startsWith("SERVICE CHARGE")
                || upper.startsWith("GST")
                || upper.startsWith("TOTAL")
                || upper.startsWith("CASH")
                || upper.startsWith("NETS")
                || upper.startsWith("VISA")
                || upper.startsWith("MASTER")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("CHANGE")
                || upper.startsWith("THANK YOU");
    }

    private int score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int score = 0;
        for (String line : text.split("\\r?\\n")) {
            String upper = normalize(line).toUpperCase();

            if (upper.matches(".*[A-Z].*")) {
                score += 3;
            }

            if (looksLikeMoneyLine(line)) {
                score += 2;
            }

            if (upper.contains("SET") || upper.contains("COMBO")) {
                score += 1;
            }
        }
        return score;
    }

    private boolean looksLikeMoneyLine(String value) {
        String cleaned = normalize(value).replace("$", "");
        return MONEY_PATTERN.matcher(cleaned).matches();
    }

    private String cleanLine(String value) {
        return normalize(value).replace("$", "");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
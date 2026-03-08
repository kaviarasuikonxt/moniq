package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class MarketWeightedItemsParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[-$]?\\d+(\\.\\d{1,3})?$");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s*KG$", Pattern.CASE_INSENSITIVE);
    @SuppressWarnings("unused")
    private static final Pattern ITEM_NUMBER_PATTERN = Pattern.compile("^\\d{5,8}$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.MARKET_WEIGHTED_ITEMS;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String rawPreferred = filterWeightedSection(input.getRawText(), false);
        String layoutAlternative = filterWeightedSection(input.getLayoutText(), true);

        if (score(rawPreferred) >= score(layoutAlternative) && !rawPreferred.isBlank()) {
            return rawPreferred;
        }

        if (!layoutAlternative.isBlank()) {
            return layoutAlternative;
        }

        if (input.getRawText() != null && !input.getRawText().isBlank()) {
            return input.getRawText();
        }

        return input.getLayoutText();
    }

    private String filterWeightedSection(String text, boolean layoutMode) {
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
                if (looksLikeWeightedItemStart(upper)) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (isFooterStart(upper)) {
                break;
            }

            if (shouldSkip(value, upper)) {
                continue;
            }

            if (layoutMode) {
                result.add(cleanLayoutLine(value));
            } else {
                result.add(value);
            }
        }

        return String.join("\n", result).trim();
    }

    private boolean looksLikeWeightedItemStart(String upper) {
        return upper.contains("DESCRIPTION")
                || upper.contains("QTY")
                || upper.contains("PRICE")
                || upper.contains("AMOUNT")
                || upper.matches(".*[A-Z].*")
                || upper.contains("KG");
    }

    private boolean shouldSkip(String value, String upper) {
        if (value.isBlank()) {
            return true;
        }

        if (DECORATOR_PATTERN.matcher(value).matches()) {
            return true;
        }

        if (upper.equals("NO DESCRIPTION")
                || upper.equals("DESCRIPTION")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("AMOUNT")
                || upper.equals("NO")) {
            return true;
        }

        if (upper.startsWith("BILL NO")
                || upper.startsWith("DATE")
                || upper.startsWith("CASHIER")
                || upper.startsWith("PH")
                || upper.startsWith("DUPLICATE COPY")
                || upper.startsWith("NET TOTAL")
                || upper.startsWith("TOTAL AMOUNT")
                || upper.startsWith("BILL DISCOUNT")
                || upper.startsWith("AMOUNT TENDERED")
                || upper.startsWith("CHANGE DUE")
                || upper.startsWith("GOODS SOLD")
                || upper.startsWith("EXCHANGEBLE")
                || upper.startsWith("COME AGAIN")
                || upper.startsWith("THANK YOU")) {
            return true;
        }

        if (upper.equals("NETS")
                || upper.equals("VISA")
                || upper.equals("MASTER")) {
            return true;
        }

        return false;
    }

    private boolean isFooterStart(String upper) {
        return upper.startsWith("TOTAL AMOUNT")
                || upper.startsWith("BILL DISCOUNT")
                || upper.startsWith("NET TOTAL")
                || upper.startsWith("AMOUNT TENDERED")
                || upper.startsWith("CHANGE DUE")
                || upper.startsWith("GOODS SOLD")
                || upper.startsWith("THANK YOU")
                || upper.startsWith("COME AGAIN");
    }

    private String cleanLayoutLine(String value) {
        return value.replace("$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String[] lines = text.split("\\r?\\n");
        int score = 0;

        for (String line : lines) {
            String value = normalize(line);
            String upper = value.toUpperCase();

            if (looksLikeItemLine(upper)) {
                score += 3;
            }

            if (looksLikeWeightLine(upper)) {
                score += 3;
            }

            if (looksLikeMoneyLine(value)) {
                score += 2;
            }

            if (looksLikeNoise(upper)) {
                score -= 4;
            }
        }

        return score;
    }

    private boolean looksLikeItemLine(String upper) {
        return upper.matches(".*[A-Z].*")
                && !looksLikeNoise(upper)
                && !upper.startsWith("TOTAL")
                && !upper.startsWith("NET")
                && !upper.startsWith("AMOUNT");
    }

    private boolean looksLikeWeightLine(String upper) {
        return WEIGHT_PATTERN.matcher(upper).matches()
                || upper.contains("KG")
                || upper.contains("/KG");
    }

    private boolean looksLikeMoneyLine(String value) {
        String cleaned = value.replace("$", "").trim();
        return MONEY_PATTERN.matcher(cleaned).matches();
    }

    private boolean looksLikeNoise(String upper) {
        return upper.contains("TENDERED")
                || upper.contains("CHANGE DUE")
                || upper.contains("DISCOUNT")
                || upper.contains("DUPLICATE COPY")
                || upper.contains("CASHIER")
                || upper.contains("BILL NO")
                || upper.contains("DATE")
                || upper.contains("THANK YOU")
                || upper.contains("COME AGAIN")
                || upper.contains("GOODS SOLD");
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
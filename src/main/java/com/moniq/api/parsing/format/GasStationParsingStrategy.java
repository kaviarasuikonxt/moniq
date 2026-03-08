package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class GasStationParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{10,14}$");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[-$]?\\d+(\\.\\d{1,3})?$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.GAS_STATION;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String layoutPreferred = filterGasStationSection(input.getLayoutText());
        String rawFallback = filterGasStationSection(input.getRawText());

        if (score(layoutPreferred) >= score(rawFallback) && !layoutPreferred.isBlank()) {
            return layoutPreferred;
        }

        if (!rawFallback.isBlank()) {
            return rawFallback;
        }

        return input.getRawText();
    }

    private String filterGasStationSection(String text) {
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
                if (upper.contains("ITEM NAME")
                        || upper.contains("DESCRIPTION")
                        || upper.contains("QTY")
                        || upper.contains("PRICE")
                        || upper.contains("TOTAL")
                        || looksLikeFuelItem(upper)
                        || looksLikeRetailItem(upper)) {
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

            result.add(cleanLine(value));
        }

        return String.join("\n", result).trim();
    }

    private boolean shouldSkip(String value, String upper) {
        if (value.isBlank()) {
            return true;
        }

        if (DECORATOR_PATTERN.matcher(value).matches()) {
            return true;
        }

        if (BARCODE_PATTERN.matcher(value).matches()) {
            return true;
        }

        return upper.equals("ITEM NAME")
                || upper.equals("DESCRIPTION")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("TOTAL")
                || upper.startsWith("STATION")
                || upper.startsWith("POS")
                || upper.startsWith("TRANSACTION")
                || upper.startsWith("RECEIPT")
                || upper.startsWith("CARD NUM")
                || upper.startsWith("APPROVAL")
                || upper.startsWith("BATCH")
                || upper.startsWith("REF:")
                || upper.startsWith("TRAN TIME")
                || upper.startsWith("AID:")
                || upper.startsWith("ARQC:")
                || upper.startsWith("DBS")
                || upper.startsWith("CONTACTLESS")
                || upper.startsWith("VISA CREDIT")
                || upper.startsWith("GST REG");
    }

    private boolean isFooterStart(String upper) {
        return upper.startsWith("DISCOUNT TOTAL")
                || upper.startsWith("TOTAL INC. GST")
                || upper.startsWith("GST AMOUNT")
                || upper.startsWith("CARD PAYMENT")
                || upper.startsWith("NETS")
                || upper.startsWith("VISA")
                || upper.startsWith("MASTER")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("THANK YOU")
                || upper.startsWith("I CONFIRM")
                || upper.startsWith("IN THE AMOUNT");
    }

    private boolean looksLikeFuelItem(String upper) {
        return upper.contains("RON")
                || upper.contains("DIESEL")
                || upper.contains("PETROL")
                || upper.contains("UNLEADED")
                || upper.contains("V-POWER");
    }

    private boolean looksLikeRetailItem(String upper) {
        return upper.matches(".*[A-Z].*")
                && !upper.startsWith("TOTAL")
                && !upper.startsWith("GST");
    }

    private int score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int score = 0;
        for (String line : text.split("\\r?\\n")) {
            String upper = normalize(line).toUpperCase();

            if (looksLikeFuelItem(upper) || looksLikeRetailItem(upper)) {
                score += 3;
            }

            if (looksLikeMoneyLine(line)) {
                score += 2;
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
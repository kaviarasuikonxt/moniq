package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class RetailGeneralParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8,14}$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.RETAIL_GENERAL;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String rawPreferred = filterRetailSection(input.getRawText());
        String layoutAlternative = filterRetailSection(input.getLayoutText());

        if (!rawPreferred.isBlank()) {
            return rawPreferred;
        }

        if (!layoutAlternative.isBlank()) {
            return layoutAlternative;
        }

        return input.getRawText();
    }

    private String filterRetailSection(String text) {
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
                if (upper.contains("DESCRIPTION")
                        || upper.contains("ITEM")
                        || upper.contains("QTY")
                        || upper.contains("PRICE")
                        || upper.contains("AMOUNT")
                        || upper.contains("NO DESCRIPTION")) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (isFooterStart(upper)) {
                break;
            }

            if (DECORATOR_PATTERN.matcher(value).matches()) {
                continue;
            }

            if (BARCODE_PATTERN.matcher(value).matches()) {
                continue;
            }

            if (upper.equals("DESCRIPTION")
                    || upper.equals("ITEM")
                    || upper.equals("QTY")
                    || upper.equals("PRICE")
                    || upper.equals("AMOUNT")
                    || upper.equals("NO DESCRIPTION")
                    || upper.equals("NO")) {
                continue;
            }

            result.add(value.replace("$", ""));
        }

        return String.join("\n", result).trim();
    }

    private boolean isFooterStart(String upper) {
        return upper.startsWith("SUBTOTAL")
                || upper.startsWith("GST")
                || upper.startsWith("TOTAL")
                || upper.startsWith("NETS")
                || upper.startsWith("VISA")
                || upper.startsWith("MASTER")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("CHANGE")
                || upper.startsWith("THANK YOU")
                || upper.startsWith("AMOUNT TENDERED")
                || upper.startsWith("CHANGE DUE");
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
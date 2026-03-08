package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BakeryParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{10,14}$");
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.BAKERY;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String preferred = filterBakerySection(input.getLayoutText());
        if (!preferred.isBlank()) {
            return preferred;
        }

        String fallback = filterBakerySection(input.getRawText());
        if (!fallback.isBlank()) {
            return fallback;
        }

        return input.getRawText();
    }

    private String filterBakerySection(String text) {
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
                        || upper.contains("NET AMT")
                        || upper.contains("PRICE")) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (isBakeryFooterStart(upper)) {
                break;
            }

            if (shouldSkip(value, upper)) {
                continue;
            }

            result.add(value);
        }

        return String.join("\n", result).trim();
    }

    private boolean shouldSkip(String value, String upper) {
        if (value.isBlank()) {
            return true;
        }

        if (BARCODE_PATTERN.matcher(value).matches()) {
            return true;
        }

        if (DECORATOR_PATTERN.matcher(value).matches()) {
            return true;
        }

        return upper.equals("ITEM NAME")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("NET AMT")
                || upper.equals("INC GST")
                || upper.equals("COMMENTS:")
                || upper.equals("SLIP:")
                || upper.equals("TERMI:")
                || upper.startsWith("STAFF NO")
                || upper.startsWith("DATE:")
                || upper.startsWith("GST REG")
                || upper.startsWith("BUSINESS REG");
    }

    private boolean isBakeryFooterStart(String upper) {
        return upper.startsWith("SUBTOTAL")
                || upper.startsWith("GST ")
                || upper.startsWith("TOTAL:")
                || upper.equals("NETS")
                || upper.startsWith("MERCHANT ID")
                || upper.startsWith("TERMINAL ID")
                || upper.startsWith("TRANS DATE/TIME")
                || upper.startsWith("TRANSACTION AMOUNT")
                || upper.startsWith("GOODS SOLDS")
                || upper.startsWith("REFUNDABLE")
                || upper.startsWith("THANK YOU");
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
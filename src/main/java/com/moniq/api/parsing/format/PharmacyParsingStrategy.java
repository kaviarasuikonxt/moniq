package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PharmacyParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{10,14}$");

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.PHARMACY;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String layoutPreferred = filterPharmacySection(input.getLayoutText());
        String rawFallback = filterPharmacySection(input.getRawText());

        if (!layoutPreferred.isBlank()) {
            return layoutPreferred;
        }

        if (!rawFallback.isBlank()) {
            return rawFallback;
        }

        return input.getRawText();
    }

    private String filterPharmacySection(String text) {
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
                if (upper.contains("ITEM")
                        || upper.contains("DESCRIPTION")
                        || upper.contains("QTY")
                        || upper.contains("PRICE")) {
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

            if (upper.equals("ITEM")
                    || upper.equals("DESCRIPTION")
                    || upper.equals("QTY")
                    || upper.equals("PRICE")
                    || upper.equals("AMOUNT")
                    || upper.startsWith("MEMBER")
                    || upper.startsWith("CARD")) {
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
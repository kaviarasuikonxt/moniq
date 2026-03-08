package com.moniq.api.parsing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SupermarketParsingStrategy implements ReceiptParsingStrategy {

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{10,14}$");
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[-$]?\\d+(\\.\\d{1,3})?$");
    private static final Pattern PROMO_PATTERN = Pattern.compile("^(\\d+X\\$.*|\\d+ FOR \\$.*)$", Pattern.CASE_INSENSITIVE);

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.SUPERMARKET;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        String layoutPreferred = filterSupermarketSection(input.getLayoutText(), true);
        String rawFiltered = filterSupermarketSection(input.getRawText(), false);

        if (score(layoutPreferred) >= score(rawFiltered) && !layoutPreferred.isBlank()) {
            return layoutPreferred;
        }

        if (!rawFiltered.isBlank()) {
            return rawFiltered;
        }

        if (input.getLayoutText() != null && !input.getLayoutText().isBlank()) {
            return input.getLayoutText();
        }

        return input.getRawText();
    }

    private String filterSupermarketSection(String text, boolean layoutMode) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();

        boolean started = false;

        for (int i = 0; i < lines.length; i++) {
            String value = normalize(lines[i]);
            if (value.isBlank()) {
                continue;
            }

            String upper = value.toUpperCase();

            if (!started) {
                if (looksLikeSupermarketItemStart(upper)) {
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

    private boolean looksLikeSupermarketItemStart(String upper) {
        return upper.contains("ITEM NAME")
                || upper.contains("NAME/ITEMNO")
                || upper.contains("DESCRIPTION")
                || upper.matches(".*[A-Z].*\\d.*G$")
                || upper.matches(".*[A-Z].*/\\d+.*")
                || upper.matches(".*[A-Z]{3,}.*");
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

        if (upper.equals("ITEM NAME")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("TOTAL")
                || upper.equals("NAME/ITEMNO")
                || upper.equals("DESCRIPTION")
                || upper.equals("RATE")
                || upper.equals("AFTER GST TX AMNT")) {
            return true;
        }

        if (upper.startsWith("LINK CARD")
                || upper.startsWith("ACNT NO.")
                || upper.startsWith("EXCHANGE ID")
                || upper.startsWith("ELIGIBLE:")
                || upper.startsWith("TRANSACTION TYPE")
                || upper.startsWith("LINKPTS")
                || upper.startsWith("SHAREHOLDER")
                || upper.startsWith("EXPIRY DATE")
                || upper.startsWith("TOTAL LINKPTS")
                || upper.startsWith("APPROVED")
                || upper.startsWith("TERMINAL:")
                || upper.startsWith("TRANSACTION DATE:")
                || upper.startsWith("TRANS. NUMBER:")
                || upper.startsWith("AUTH. CODE:")
                || upper.startsWith("NO SIGNATURE REQUIRED")
                || upper.startsWith("TOTAL SAVINGS")
                || upper.startsWith("TOTAL ITEMS:")
                || upper.startsWith("THANK YOU")
                || upper.startsWith("KEEP RECEIPT")
                || upper.startsWith("SEE IN-STORE")
                || upper.startsWith("MEMBERS FIRST")
                || upper.startsWith("#EVERYWORKERMATTERS")
                || upper.startsWith("CHEN ")
                || upper.startsWith("ST:")
                || upper.startsWith("RG:")
                || upper.startsWith("CH:")
                || upper.startsWith("TR:")) {
            return true;
        }

        if (upper.equals("GST")
                || upper.startsWith("GST ")
                || upper.contains("GST NO")
                || upper.contains("UEN NO")) {
            return true;
        }

        if (PROMO_PATTERN.matcher(upper).matches()) {
            return false;
        }

        return false;
    }

    private boolean isFooterStart(String upper) {
        return upper.startsWith("TOTAL")
                || upper.startsWith("MASTER")
                || upper.startsWith("VISA")
                || upper.startsWith("NETS")
                || upper.startsWith("CARD")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("RATE")
                || upper.startsWith("AFTER GST")
                || upper.startsWith("GST")
                || upper.startsWith("TOTAL ITEMS")
                || upper.startsWith("THANK YOU");
    }

    private String cleanLayoutLine(String value) {
        String cleaned = value.replace("$", "")
                .replaceAll("\\s+", " ")
                .trim();

        // normalize weird OCR promo or amount spacing
        cleaned = cleaned.replace(" / ", "/");
        cleaned = cleaned.replace(" X ", "X");
        return cleaned;
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

            if (looksLikePriceLine(value)) {
                score += 2;
            }

            if (PROMO_PATTERN.matcher(upper).matches()) {
                score += 1;
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
                && !upper.startsWith("GST")
                && !upper.startsWith("PAYMENT");
    }

    private boolean looksLikePriceLine(String value) {
        String cleaned = value.replace("$", "").trim();
        return MONEY_PATTERN.matcher(cleaned).matches()
                || cleaned.matches("^\\d+X\\d+(\\.\\d+)?/UNIT$")
                || cleaned.matches("^\\d+ FOR \\d+(\\.\\d+)?$");
    }

    private boolean looksLikeNoise(String upper) {
        return upper.contains("LINKPTS")
                || upper.contains("SHAREHOLDER")
                || upper.contains("ACNT NO")
                || upper.contains("AUTH. CODE")
                || upper.contains("APPROVED")
                || upper.contains("TERMINAL")
                || upper.contains("TRANSACTION DATE")
                || upper.contains("TRANS. NUMBER")
                || upper.contains("TOTAL SAVINGS")
                || upper.contains("THANK YOU")
                || upper.contains("KEEP RECEIPT")
                || upper.contains("MEMBERS FIRST");
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
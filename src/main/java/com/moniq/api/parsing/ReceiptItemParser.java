package com.moniq.api.parsing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ReceiptItemParser {

    private static final Pattern MONEY_PATTERN = Pattern.compile("[-$]?\\d+(\\.\\d{1,3})?");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("\\d+(\\.\\d+)?\\s*KG", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#.:]{2,}$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8,14}$");

    public List<ParsedItem> parseReceipt(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> lines = normalizeLines(text);
        List<ParsedItem> result = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (shouldSkipLine(line)) {
                i++;
                continue;
            }

            // 1) One-line compact row: bakery / compact supermarket
            ParsedItem oneLine = tryParseSingleLineRow(line);
            if (oneLine != null) {
                result.add(oneLine);
                i++;
                continue;
            }

            // 2) Weighted produce block
            ParsedItem weighted = tryParseWeightedBlock(lines, i);
            if (weighted != null) {
                result.add(weighted);
                i += weighted.getConsumedLines();
                continue;
            }

            // 3) UStar multiline block
            ParsedItem ustar = tryParseUstarBlock(lines, i);
            if (ustar != null) {
                result.add(ustar);
                i += ustar.getConsumedLines();
                continue;
            }

            // 4) Generic multiline item block
            ParsedItem generic = tryParseGenericMultilineBlock(lines, i);
            if (generic != null) {
                result.add(generic);
                i += generic.getConsumedLines();
                continue;
            }

            i++;
        }

        return compact(result);
    }

    private ParsedItem tryParseSingleLineRow(String line) {
        String cleaned = normalize(line);

        if (shouldSkipLine(cleaned) || !containsLetter(cleaned)) {
            return null;
        }

        List<String> tokens = splitTokens(cleaned);
        if (tokens.size() < 2) {
            return null;
        }

        List<String> numericTokens = new ArrayList<>();
        List<String> textTokens = new ArrayList<>();

        for (String token : tokens) {
            String t = cleanNumericToken(token);
            if (looksLikeMoney(t) || looksLikeQuantity(t) || looksLikeWeight(t)) {
                numericTokens.add(t);
            } else {
                textTokens.add(token);
            }
        }

        if (textTokens.isEmpty() || numericTokens.isEmpty()) {
            return null;
        }

        // Bakery: NAME QTY PRICE AMOUNT
        if (numericTokens.size() >= 3) {
            BigDecimal qty = tryDecimal(numericTokens.get(0));
            BigDecimal unitPrice = tryDecimal(numericTokens.get(1));
            BigDecimal amount = tryDecimal(numericTokens.get(2));

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        String.join(" ", textTokens),
                        cleaned,
                        qty,
                        unitPrice,
                        amount,
                        1
                );
            }
        }

        // Compact supermarket: NAME PRICE
        if (numericTokens.size() == 1) {
            BigDecimal amount = tryDecimal(numericTokens.get(0));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        String.join(" ", textTokens),
                        cleaned,
                        BigDecimal.ONE,
                        amount,
                        amount,
                        1
                );
            }
        }

        // Weighted compact: NAME WEIGHT AMOUNT
        if (numericTokens.size() == 2) {
            BigDecimal first = tryDecimal(stripWeightSuffix(numericTokens.get(0)));
            BigDecimal second = tryDecimal(stripWeightSuffix(numericTokens.get(1)));

            if (second != null && second.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        String.join(" ", textTokens),
                        cleaned,
                        first,
                        null,
                        second,
                        1
                );
            }
        }

        return null;
    }

    private ParsedItem tryParseWeightedBlock(List<String> lines, int index) {
        if (index >= lines.size()) {
            return null;
        }

        String line1 = lines.get(index);
        if (!containsLetter(line1) || shouldSkipLine(line1)) {
            return null;
        }

        String line2 = getLine(lines, index + 1);
        String line3 = getLine(lines, index + 2);
        String line4 = getLine(lines, index + 3);

        // Patterns seen:
        // Kadalai
        // 1
        // 0.318kg
        // 1.75
        if (looksLikeInteger(line2) && looksLikeWeight(line3) && looksLikeMoney(line4)) {
            BigDecimal qty = tryDecimal(cleanNumericToken(line2));
            BigDecimal amount = tryDecimal(cleanNumericToken(line4));

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        line1,
                        joinRawLines(line1, line2, line3, line4),
                        qty,
                        null,
                        amount,
                        4
                );
            }
        }

        // Pattern:
        // Guava India
        // 0.216kg
        // 1.19
        if (looksLikeWeight(line2) && looksLikeMoney(line3)) {
            BigDecimal qty = tryDecimal(stripWeightSuffix(cleanNumericToken(line2)));
            BigDecimal amount = tryDecimal(cleanNumericToken(line3));

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        line1,
                        joinRawLines(line1, line2, line3),
                        qty,
                        null,
                        amount,
                        3
                );
            }
        }

        return null;
    }

    private ParsedItem tryParseUstarBlock(List<String> lines, int index) {
        if (index >= lines.size()) {
            return null;
        }

        String line1 = lines.get(index);
        if (!containsLetter(line1) || shouldSkipLine(line1)) {
            return null;
        }

        String line2 = getLine(lines, index + 1);
        String line3 = getLine(lines, index + 2);
        String line4 = getLine(lines, index + 3);
        String line5 = getLine(lines, index + 4);

        // Example:
        // SENG CHOON UKRAINE EGGS 305/888843677779
        // 4
        // 6.90
        // 1.000
        // 6.90
        if (looksLikeInteger(line2) && looksLikeMoney(line3) && looksLikeMoney(line4) && looksLikeMoney(line5)) {
            BigDecimal unitPrice = tryDecimal(cleanNumericToken(line3));
            BigDecimal qty = tryDecimal(cleanNumericToken(line4));
            BigDecimal amount = tryDecimal(cleanNumericToken(line5));

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        stripTrailingBarcodeFromName(line1),
                        joinRawLines(line1, line2, line3, line4, line5),
                        qty,
                        unitPrice,
                        amount,
                        5
                );
            }
        }

        // Example:
        // M'SIA BABY SPINACH 苋菜苗 250G/955592040
        // 3796
        // 1.80 2.000
        // 3.60
        if (looksLikeInteger(line2) && looksLikeCombinedPriceQty(line3) && looksLikeMoney(line4)) {
            String[] pq = splitPriceQty(line3);
            if (pq != null) {
                BigDecimal unitPrice = tryDecimal(pq[0]);
                BigDecimal qty = tryDecimal(pq[1]);
                BigDecimal amount = tryDecimal(cleanNumericToken(line4));

                if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    return ParsedItem.of(
                            stripTrailingBarcodeFromName(line1),
                            joinRawLines(line1, line2, line3, line4),
                            qty,
                            unitPrice,
                            amount,
                            4
                    );
                }
            }
        }

        return null;
    }

    private ParsedItem tryParseGenericMultilineBlock(List<String> lines, int index) {
        if (index >= lines.size()) {
            return null;
        }

        String name = lines.get(index);
        if (!containsLetter(name) || shouldSkipLine(name)) {
            return null;
        }

        String line2 = getLine(lines, index + 1);
        String line3 = getLine(lines, index + 2);
        String line4 = getLine(lines, index + 3);

        // NAME / qty / price / amount
        if (looksLikeQuantity(line2) && looksLikeMoney(line3) && looksLikeMoney(line4)) {
            BigDecimal qty = tryDecimal(cleanNumericToken(line2));
            BigDecimal unitPrice = tryDecimal(cleanNumericToken(line3));
            BigDecimal amount = tryDecimal(cleanNumericToken(line4));

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        name,
                        joinRawLines(name, line2, line3, line4),
                        qty,
                        unitPrice,
                        amount,
                        4
                );
            }
        }

        // NAME / amount only
        if (looksLikeMoney(line2)) {
            BigDecimal amount = tryDecimal(cleanNumericToken(line2));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ParsedItem.of(
                        name,
                        joinRawLines(name, line2),
                        BigDecimal.ONE,
                        amount,
                        amount,
                        2
                );
            }
        }

        return null;
    }

    private List<ParsedItem> compact(List<ParsedItem> items) {
        List<ParsedItem> result = new ArrayList<>();

        for (ParsedItem item : items) {
            if (item == null) {
                continue;
            }

            if (item.getItemName() == null || item.getItemName().isBlank()) {
                continue;
            }

            if (looksLikeNoise(item.getItemName())) {
                continue;
            }

            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            result.add(item);
        }

        return result;
    }

    private List<String> normalizeLines(String text) {
        String[] raw = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();

        for (String line : raw) {
            String value = normalize(line);
            if (!value.isBlank()) {
                result.add(value);
            }
        }

        return result;
    }

    private boolean shouldSkipLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }

        String upper = line.toUpperCase(Locale.ROOT);

        if (DECORATOR_PATTERN.matcher(line).matches()) {
            return true;
        }

        if (BARCODE_PATTERN.matcher(line).matches()) {
            return true;
        }

        return upper.equals("ITEM")
                || upper.equals("ITEM NAME")
                || upper.equals("DESCRIPTION")
                || upper.equals("NAME/ITEMNO")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("TOTAL")
                || upper.equals("NET AMT")
                || upper.equals("INC GST")
                || upper.startsWith("SUBTOTAL")
                || upper.startsWith("GST")
                || upper.startsWith("TOTAL:")
                || upper.startsWith("TOTAL ")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("NETS")
                || upper.startsWith("VISA")
                || upper.startsWith("MASTER")
                || upper.startsWith("CARD")
                || upper.startsWith("CASH")
                || upper.startsWith("CHANGE")
                || upper.startsWith("THANK YOU")
                || upper.startsWith("LINK CARD")
                || upper.startsWith("LINKPTS")
                || upper.startsWith("SHAREHOLDER")
                || upper.startsWith("APPROVED")
                || upper.startsWith("TERMINAL")
                || upper.startsWith("TRANSACTION")
                || upper.startsWith("AUTH.")
                || upper.startsWith("REMARK")
                || upper.startsWith("GOODS SOLD")
                || upper.startsWith("MERCHANT ID")
                || upper.startsWith("TRANS DATE/TIME")
                || upper.startsWith("TRANSACTION AMOUNT")
                || upper.startsWith("AMOUNT TENDERED")
                || upper.startsWith("CHANGE DUE");
    }

    private boolean looksLikeNoise(String value) {
        String upper = normalize(value).toUpperCase(Locale.ROOT);

        return upper.contains("TOTAL")
                || upper.contains("GST")
                || upper.contains("PAYMENT")
                || upper.contains("NETS")
                || upper.contains("VISA")
                || upper.contains("MASTER")
                || upper.contains("LINKPTS")
                || upper.contains("APPROVED")
                || upper.contains("THANK YOU")
                || upper.contains("TENDERED")
                || upper.contains("CHANGE")
                || upper.contains("DISCOUNT")
                || upper.contains("TRANSACTION")
                || upper.contains("TERMINAL")
                || upper.contains("AUTH.")
                || upper.contains("REMARK")
                || upper.contains("GOODS SOLD");
    }

    private boolean containsLetter(String value) {
        return value != null && value.matches(".*[A-Za-z].*");
    }

    private boolean looksLikeMoney(String value) {
        String cleaned = cleanNumericToken(value);
        return cleaned != null && MONEY_PATTERN.matcher(cleaned).matches();
    }

    private boolean looksLikeQuantity(String value) {
        String cleaned = cleanNumericToken(value);
        return cleaned != null && (MONEY_PATTERN.matcher(cleaned).matches() || INTEGER_PATTERN.matcher(cleaned).matches());
    }

    private boolean looksLikeInteger(String value) {
        String cleaned = cleanNumericToken(value);
        return cleaned != null && INTEGER_PATTERN.matcher(cleaned).matches();
    }

    private boolean looksLikeWeight(String value) {
        String cleaned = normalize(value).toUpperCase(Locale.ROOT).replace(" ", "");
        return WEIGHT_PATTERN.matcher(cleaned).matches();
    }

    private boolean looksLikeCombinedPriceQty(String value) {
        String normalized = normalize(value).replace("$", "");
        String[] parts = normalized.split("\\s+");
        if (parts.length != 2) {
            return false;
        }
        return looksLikeMoney(parts[0]) && looksLikeMoney(parts[1]);
    }

    private String[] splitPriceQty(String value) {
        String normalized = normalize(value).replace("$", "");
        String[] parts = normalized.split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        if (!looksLikeMoney(parts[0]) || !looksLikeMoney(parts[1])) {
            return null;
        }
        return new String[]{cleanNumericToken(parts[0]), cleanNumericToken(parts[1])};
    }

    private String stripTrailingBarcodeFromName(String name) {
        List<String> tokens = splitTokens(normalize(name));
        if (tokens.isEmpty()) {
            return normalize(name);
        }

        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (BARCODE_PATTERN.matcher(token).matches()) {
                continue;
            }
            result.add(token);
        }

        return String.join(" ", result).trim();
    }

    private String cleanNumericToken(String token) {
        if (token == null) {
            return "";
        }

        return token.replace("$", "")
                .replace(",", ".")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String stripWeightSuffix(String token) {
        if (token == null) {
            return "";
        }

        return token.toUpperCase(Locale.ROOT)
                .replace("KG", "")
                .trim();
    }

    private BigDecimal tryDecimal(String value) {
        try {
            String cleaned = cleanNumericToken(value);
            if (cleaned == null || cleaned.isBlank() || cleaned.equals("-")) {
                return null;
            }
            return new BigDecimal(cleaned);
        } catch (Exception ex) {
            return null;
        }
    }

    private String getLine(List<String> lines, int index) {
        if (index < 0 || index >= lines.size()) {
            return "";
        }
        return lines.get(index);
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

    private List<String> splitTokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }

        String[] parts = normalized.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private String joinRawLines(String... lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String value = normalize(line);
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return String.join(" | ", result);
    }

    public static class ParsedItem {
        private final String itemName;
        private final String rawLine;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal amount;
        private final int consumedLines;

        public ParsedItem(
                String itemName,
                String rawLine,
                BigDecimal quantity,
                BigDecimal unitPrice,
                BigDecimal amount,
                int consumedLines
        ) {
            this.itemName = itemName == null ? "" : itemName.trim();
            this.rawLine = rawLine == null ? "" : rawLine.trim();
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.amount = amount;
            this.consumedLines = consumedLines <= 0 ? 1 : consumedLines;
        }

        public static ParsedItem of(
                String itemName,
                String rawLine,
                BigDecimal quantity,
                BigDecimal unitPrice,
                BigDecimal amount,
                int consumedLines
        ) {
            return new ParsedItem(itemName, rawLine, quantity, unitPrice, amount, consumedLines);
        }

        public String getItemName() {
            return itemName;
        }

        public String getRawLine() {
            return rawLine;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public int getConsumedLines() {
            return consumedLines;
        }
    }
}
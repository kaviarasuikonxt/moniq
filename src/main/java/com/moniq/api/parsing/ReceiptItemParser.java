package com.moniq.api.parsing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptItemParser {

    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+(?:\\.\\d+)?$");
    private static final Pattern ITEM_CODE_PATTERN = Pattern.compile("^\\d{1,8}$");
    private static final Pattern ALPHA_PATTERN = Pattern.compile(".*[A-Za-z].*");

    // Single-line table format:
    // ITEM NAME    QTY    PRICE    TOTAL
    // Example:
    // Roti Boy Bun eac 1 1.70 1.70
    private static final Pattern SINGLE_LINE_ITEM_PATTERN =
            Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d{1,3})?)\\s+(\\d+(?:\\.\\d{1,2})?)\\s+(\\d+(?:\\.\\d{1,2})?)$");

    private static final List<String> NOISE_WORDS = List.of(
            "u stars", "branch", "cashier", "saleman", "name/itemno", "price", "qty",
            "total", "number", "originalamount", "discountamount", "specialamount",
            "gst", "pay", "remark", "feedback", "store", "member", "loyalty",
            "visa", "mastercard", "cash", "change", "receipt", "reg no",
            "sent to", "singapore", "link", "default",
            "slip", "termi", "staff no", "comments", "item name", "net amt",
            "inc gst", "business reg", "merchant id", "terminal id", "trans date/time",
            "transaction amount", "goods solds are not returnable", "thank you"
    );

    public ParsedItem parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String raw = clean(line);
        if (raw.isBlank() || isNoise(raw)) {
            return null;
        }

        // 1) First try single-line table parser
        ParsedItem singleLine = parseSingleLineItem(raw, line);
        if (singleLine != null) {
            return singleLine;
        }

        // 2) Fallback to old single-line amount-at-end logic
        String[] parts = raw.split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        BigDecimal amount = tryParseAmount(parts[parts.length - 1]);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal quantity = BigDecimal.ONE;
        String itemName = raw.substring(0, raw.lastIndexOf(parts[parts.length - 1])).trim();

        if (parts.length >= 3) {
            BigDecimal possibleQty = tryParseQuantity(parts[parts.length - 2]);
            if (possibleQty != null) {
                quantity = possibleQty;
                itemName = raw.substring(0, raw.lastIndexOf(parts[parts.length - 2])).trim();
            }
        }

        itemName = normalizeItemName(itemName);
        if (itemName.isBlank()) {
            return null;
        }

        ParsedItem item = new ParsedItem();
        item.setRawLine(line);
        item.setItemName(itemName);
        item.setQuantity(quantity);
        item.setAmount(amount);
        item.setUnitPrice(
                quantity.compareTo(BigDecimal.ZERO) > 0
                        ? amount.divide(quantity, 2, RoundingMode.HALF_UP)
                        : amount
        );

        return item;
    }

    public List<ParsedItem> parseReceipt(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<String> lines = splitAndClean(rawText);
        List<ParsedItem> items = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // 1) First try direct single-line item parsing
            ParsedItem singleLine = parseSingleLineItem(line, line);
            if (singleLine != null) {
                items.add(singleLine);
                continue;
            }

            // 2) Then try multi-line item parsing
            if (!looksLikeItemName(line)) {
                continue;
            }

            String itemName = normalizeItemName(line);
            if (itemName.isBlank()) {
                continue;
            }

            ScanResult scan = scanForward(lines, i + 1);
            if (scan == null || scan.total == null || scan.total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            ParsedItem item = new ParsedItem();
            item.setRawLine(line);
            item.setItemName(itemName);
            item.setQuantity(scan.quantity != null ? scan.quantity : BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP));
            item.setUnitPrice(scan.unitPrice != null
                    ? scan.unitPrice
                    : scan.total.divide(item.getQuantity(), 2, RoundingMode.HALF_UP));
            item.setAmount(scan.total);

            items.add(item);

            i = scan.lastConsumedIndex;
        }

        return items;
    }

    private ParsedItem parseSingleLineItem(String cleanedLine, String rawLine) {
        Matcher matcher = SINGLE_LINE_ITEM_PATTERN.matcher(cleanedLine);
        if (!matcher.matches()) {
            return null;
        }

        String possibleName = matcher.group(1);
        BigDecimal qty = tryParseQuantity(matcher.group(2));
        BigDecimal unitPrice = tryParseAmount(matcher.group(3));
        BigDecimal total = tryParseAmount(matcher.group(4));

        if (qty == null || unitPrice == null || total == null) {
            return null;
        }

        String itemName = normalizeItemName(possibleName);
        if (itemName.isBlank() || isNoise(itemName)) {
            return null;
        }

        ParsedItem item = new ParsedItem();
        item.setRawLine(rawLine);
        item.setItemName(itemName);
        item.setQuantity(qty.setScale(3, RoundingMode.HALF_UP));
        item.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
        item.setAmount(total.setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private ScanResult scanForward(List<String> lines, int start) {
        BigDecimal unitPrice = null;
        BigDecimal quantity = null;
        BigDecimal total = null;
        int consumed = start - 1;

        for (int j = start; j < Math.min(start + 5, lines.size()); j++) {
            String next = lines.get(j);
            String cleaned = clean(next);

            if (cleaned.isBlank()) {
                continue;
            }

            // if next line itself is a single-line item, stop current multiline scan
            if (parseSingleLineItem(cleaned, cleaned) != null) {
                break;
            }

            if (looksLikeItemName(cleaned)) {
                break;
            }

            if (isStopSection(cleaned)) {
                break;
            }

            if (ITEM_CODE_PATTERN.matcher(cleaned).matches()) {
                consumed = j;
                continue;
            }

            String[] pair = cleaned.split("\\s+");
            if (pair.length == 2) {
                BigDecimal p1 = tryParseAmount(pair[0]);
                BigDecimal p2 = tryParseQuantity(pair[1]);
                if (p1 != null && p2 != null) {
                    unitPrice = p1.setScale(2, RoundingMode.HALF_UP);
                    quantity = p2.setScale(3, RoundingMode.HALF_UP);
                    consumed = j;
                    continue;
                }
            }

            BigDecimal n = tryParseAmount(cleaned);
            if (n == null) {
                continue;
            }

            if (unitPrice == null) {
                unitPrice = n.setScale(2, RoundingMode.HALF_UP);
                consumed = j;
                continue;
            }

            if (quantity == null && looksLikeQuantity(cleaned)) {
                quantity = n.setScale(3, RoundingMode.HALF_UP);
                consumed = j;
                continue;
            }

            if (total == null) {
                total = n.setScale(2, RoundingMode.HALF_UP);
                consumed = j;
                continue;
            }
        }

        if (total == null) {
            return null;
        }

        ScanResult r = new ScanResult();
        r.unitPrice = unitPrice;
        r.quantity = quantity != null ? quantity : BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
        r.total = total;
        r.lastConsumedIndex = consumed;
        return r;
    }

    private List<String> splitAndClean(String rawText) {
        String[] arr = rawText.split("\\r?\\n");
        List<String> out = new ArrayList<>();
        for (String line : arr) {
            String cleaned = clean(line);
            if (!cleaned.isBlank()) {
                out.add(cleaned);
            }
        }
        return out;
    }

    private String clean(String line) {
        if (line == null) {
            return "";
        }

        return line
                .replace('：', ':')
                .replace('；', ';')
                .replaceAll("(\\d)\\.\\s+(\\d)", "$1.$2")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isNoise(String line) {
        String v = line.toLowerCase(Locale.ROOT).trim();

        if (v.isBlank() || v.length() <= 1) {
            return true;
        }

        if (v.contains("@")) {
            return true;
        }

        if (v.matches("^\\d{10,}$")) {
            return true;
        }

        if (v.matches("^[\\-_=]{3,}$")) {
            return true;
        }

        for (String word : NOISE_WORDS) {
            if (v.contains(word)) {
                return true;
            }
        }

        return false;
    }

    private boolean isStopSection(String line) {
        String v = line.toLowerCase(Locale.ROOT);
        return v.contains("qty:")
                || v.contains("subtotal")
                || v.contains("originalamount")
                || v.contains("discountamount")
                || v.contains("specialamount")
                || v.contains("gst")
                || v.contains("pay:")
                || v.contains("nets")
                || v.contains("remark")
                || v.contains("total $");
    }

    private boolean looksLikeItemName(String line) {
        String v = line.toLowerCase(Locale.ROOT);

        if (!ALPHA_PATTERN.matcher(v).matches()) {
            return false;
        }

        if (isNoise(v)) {
            return false;
        }

        if (PURE_NUMBER.matcher(v).matches()) {
            return false;
        }

        if (ITEM_CODE_PATTERN.matcher(v).matches()) {
            return false;
        }

        return true;
    }

    private boolean looksLikeQuantity(String line) {
        BigDecimal n = tryParseAmount(line);
        if (n == null) {
            return false;
        }

        return n.compareTo(BigDecimal.ZERO) > 0
                && n.compareTo(new BigDecimal("20.000")) <= 0
                && line.contains(".");
    }

    private BigDecimal tryParseAmount(String text) {
        if (text == null) {
            return null;
        }

        String v = text.trim()
                .replace(",", "")
                .replaceAll("\\s+", "");

        if (!v.matches("^\\d+(?:\\.\\d{1,3})?$")) {
            return null;
        }

        try {
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal tryParseQuantity(String text) {
        BigDecimal n = tryParseAmount(text);
        if (n == null) {
            return null;
        }

        if (n.compareTo(BigDecimal.ZERO) <= 0 || n.compareTo(new BigDecimal("50")) > 0) {
            return null;
        }

        return n.setScale(3, RoundingMode.HALF_UP);
    }

    private String normalizeItemName(String itemName) {
        if (itemName == null) {
            return "";
        }

        String v = itemName.toLowerCase(Locale.ROOT);

        v = v.replaceAll("[^a-z0-9\\s']", " ");
        v = v.replaceAll("\\b\\d{3,}\\b", " ");
        v = v.replaceAll("\\b\\d+g\\b", " ");
        v = v.replaceAll("\\s+", " ").trim();

        v = v.replaceAll("\\bm'sia\\b", "malaysia");
        v = v.replaceAll("\\bmsia\\b", "malaysia");

        return v;
    }

    public static class ParsedItem {
        private String rawLine;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;

        public String getRawLine() {
            return rawLine;
        }

        public void setRawLine(String rawLine) {
            this.rawLine = rawLine;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    private static class ScanResult {
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal total;
        private int lastConsumedIndex;
    }
}
package com.moniq.api.parsing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ReceiptItemParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("^\\d+\\.?\\d{0,2}$");
    private static final Pattern QTY_PATTERN = Pattern.compile("^\\d+\\.?\\d{0,3}$");
    private static final Pattern ITEM_CODE_PATTERN = Pattern.compile("^\\d{1,8}$");
    private static final Pattern ALPHA_PATTERN = Pattern.compile(".*[A-Za-z].*");

    private static final List<String> NOISE_WORDS = List.of(
            "u stars", "branch", "cashier", "saleman", "name/itemno", "price", "qty",
            "total", "number", "originalamount", "discountamount", "specialamount",
            "gst", "pay", "remark", "feedback", "store", "member", "loyalty",
            "visa", "mastercard", "cash", "change", "receipt", "reg no"
    );

    /**
     * Backward-compatible single-line parser.
     * Keeps your current OcrService from breaking immediately.
     */
    public ParsedItem parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String raw = clean(line);
        if (raw.isBlank() || isNoise(raw)) {
            return null;
        }

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
                itemName = raw.substring(0,
                        raw.lastIndexOf(parts[parts.length - 2])).trim();
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

    /**
     * New full-receipt parser.
     * Use this in OcrService for better results on multi-line receipts.
     */
    public List<ParsedItem> parseReceipt(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<String> lines = splitAndClean(rawText);
        List<ParsedItem> items = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (isNoise(line)) {
                continue;
            }

            if (!looksLikeItemName(line)) {
                continue;
            }

            String itemName = normalizeItemName(line);
            if (itemName.isBlank()) {
                continue;
            }

            BigDecimal qty = null;
            BigDecimal total = null;
            BigDecimal unitPrice = null;

            // Look ahead up to next 4 lines for code / unit price / qty / total
            for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                String next = lines.get(j);

                if (isNoise(next)) {
                    break;
                }

                if (ITEM_CODE_PATTERN.matcher(next).matches()) {
                    continue;
                }

                // Case: "1.80 2.000"
                String[] pair = next.split("\\s+");
                if (pair.length == 2) {
                    BigDecimal p1 = tryParseAmount(pair[0]);
                    BigDecimal p2 = tryParseQuantity(pair[1]);
                    if (p1 != null && p2 != null) {
                        unitPrice = p1;
                        qty = p2;
                        continue;
                    }
                }

                // single numeric line
                BigDecimal numeric = tryParseAmount(next);
                if (numeric != null) {
                    // Heuristic:
                    // first decimal with <= 3 scale and around 1/2/3 usually qty
                    // last decimal usually total
                    if (qty == null && looksLikeQuantity(next)) {
                        qty = normalizeScale(numeric, 3);
                        continue;
                    }

                    if (unitPrice == null && total == null) {
                        unitPrice = normalizeScale(numeric, 2);
                        continue;
                    }

                    if (total == null) {
                        total = normalizeScale(numeric, 2);
                    }
                }

                // stop if another possible item line starts
                if (looksLikeItemName(next) && !PRICE_PATTERN.matcher(next).matches()) {
                    break;
                }
            }

            // U Star-style fallback:
            // ITEM
            // code
            // price
            // qty
            // total
            if (total == null) {
                ScanResult scan = scanForwardForTotals(lines, i + 1);
                if (scan != null) {
                    if (qty == null) {
                        qty = scan.quantity;
                    }
                    if (unitPrice == null) {
                        unitPrice = scan.unitPrice;
                    }
                    if (total == null) {
                        total = scan.total;
                    }
                }
            }

            if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                qty = BigDecimal.ONE;
            }

            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                unitPrice = total.divide(qty, 2, RoundingMode.HALF_UP);
            }

            ParsedItem item = new ParsedItem();
            item.setRawLine(line);
            item.setItemName(itemName);
            item.setQuantity(qty);
            item.setAmount(total);
            item.setUnitPrice(unitPrice);

            items.add(item);

            // Skip scanned lines to reduce duplicates
            i = Math.min(i + 4, lines.size() - 1);
        }

        return items;
    }

    private ScanResult scanForwardForTotals(List<String> lines, int start) {
        BigDecimal unitPrice = null;
        BigDecimal qty = null;
        BigDecimal total = null;

        for (int j = start; j < Math.min(start + 4, lines.size()); j++) {
            String next = lines.get(j);

            if (isNoise(next)) {
                break;
            }

            if (ITEM_CODE_PATTERN.matcher(next).matches()) {
                continue;
            }

            String[] pair = next.split("\\s+");
            if (pair.length == 2) {
                BigDecimal p1 = tryParseAmount(pair[0]);
                BigDecimal p2 = tryParseQuantity(pair[1]);
                if (p1 != null && p2 != null) {
                    unitPrice = p1;
                    qty = normalizeScale(p2, 3);
                    continue;
                }
            }

            BigDecimal n = tryParseAmount(next);
            if (n == null) {
                continue;
            }

            if (unitPrice == null) {
                unitPrice = normalizeScale(n, 2);
                continue;
            }

            if (qty == null && looksLikeQuantity(next)) {
                qty = normalizeScale(n, 3);
                continue;
            }

            if (total == null) {
                total = normalizeScale(n, 2);
            }
        }

        if (total == null) {
            return null;
        }

        ScanResult r = new ScanResult();
        r.unitPrice = unitPrice;
        r.quantity = qty;
        r.total = total;
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
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*\\.\\s*", ".")
                .trim();
    }

    private boolean isNoise(String line) {
        String v = line.toLowerCase(Locale.ROOT).trim();

        if (v.isBlank()) {
            return true;
        }

        if (v.length() <= 1) {
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

    private boolean looksLikeItemName(String line) {
        String v = line.toLowerCase(Locale.ROOT);

        if (!ALPHA_PATTERN.matcher(v).matches()) {
            return false;
        }

        if (isNoise(v)) {
            return false;
        }

        // Avoid pure numeric / amount / qty lines
        if (PRICE_PATTERN.matcher(v).matches()) {
            return false;
        }

        if (ITEM_CODE_PATTERN.matcher(v).matches()) {
            return false;
        }

        return true;
    }

    private boolean looksLikeQuantity(String line) {
        String v = line.trim();
        if (!QTY_PATTERN.matcher(v).matches()) {
            return false;
        }

        try {
            BigDecimal n = new BigDecimal(v);
            return n.compareTo(BigDecimal.ZERO) > 0
                    && n.compareTo(new BigDecimal("20.000")) <= 0
                    && v.contains(".");
        } catch (Exception e) {
            return false;
        }
    }

    private BigDecimal tryParseAmount(String text) {
        if (text == null) {
            return null;
        }

        String v = text.trim()
                .replace(",", "")
                .replaceAll("\\s+", "");

        if (!v.matches("^\\d+\\.?\\d{0,3}$")) {
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

        return normalizeScale(n, 3);
    }

    private BigDecimal normalizeScale(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private String normalizeItemName(String itemName) {
        if (itemName == null) {
            return "";
        }

        String v = itemName.toLowerCase(Locale.ROOT);

        v = v.replaceAll("[^a-z0-9\\s']", " ");
        v = v.replaceAll("\\b\\d{3,}\\b", " ");      // remove long codes
        v = v.replaceAll("\\b\\d+g\\b", " ");        // remove 250g style
        v = v.replaceAll("\\bqty\\b", " ");
        v = v.replaceAll("\\bprice\\b", " ");
        v = v.replaceAll("\\btotal\\b", " ");
        v = v.replaceAll("\\s+", " ").trim();

        // Optional lightweight abbreviation cleanup
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
    }
}
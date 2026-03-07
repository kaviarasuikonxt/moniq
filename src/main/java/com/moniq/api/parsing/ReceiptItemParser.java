package com.moniq.api.parsing;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptItemParser {

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d+\\.\\d{2})$");

    private static final Pattern QTY_PATTERN =
            Pattern.compile("(\\d+)x");

    public ParsedItem parse(String line) {

        if (line == null || line.isBlank()) {
            return null;
        }

        BigDecimal price = extractPrice(line);
        BigDecimal qty = extractQuantity(line);

        String name = line
                .replaceAll("\\d+\\.\\d{2}$", "")
                .replaceAll("\\d+x", "")
                .trim();

        ParsedItem item = new ParsedItem();
        item.setRawLine(line);
        item.setItemName(name);
        item.setQuantity(qty);
        item.setAmount(price);

        return item;
    }

    private BigDecimal extractPrice(String line) {

        Matcher matcher = PRICE_PATTERN.matcher(line);

        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal extractQuantity(String line) {

        Matcher matcher = QTY_PATTERN.matcher(line.toLowerCase());

        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }

        return BigDecimal.ONE;
    }

    @Data
    public static class ParsedItem {

        private String rawLine;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal amount;
    }
}
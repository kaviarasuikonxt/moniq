package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class OcrRowTextComposer {

    /**
     * Barcode patterns (EAN / UPC etc.)
     */
    private static final Pattern BARCODE_PATTERN =
            Pattern.compile("^\\d{10,14}$");

    /**
     * Numeric values (price, qty, totals)
     */
    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("^[-$]?\\d+(\\.\\d+)?$");

    public String composeDocumentText(List<OcrRow> rows) {

        if (rows == null || rows.isEmpty()) {
            return "";
        }

        List<String> result = new ArrayList<>();

        for (OcrRow row : rows) {

            if (row == null || row.getCells() == null || row.getCells().isEmpty()) {
                continue;
            }

            List<String> texts = new ArrayList<>();

            for (OcrCell cell : row.getCells()) {

                String text = normalize(cell.getText());

                if (text.isBlank()) {
                    continue;
                }

                // remove barcodes
                if (BARCODE_PATTERN.matcher(text).matches()) {
                    continue;
                }

                texts.add(text);
            }

            if (texts.isEmpty()) {
                continue;
            }

            String merged = mergeRow(texts);

            if (!merged.isBlank()) {
                result.add(merged);
            }
        }

        return String.join("\n", result);
    }

    /**
     * Merge cells into logical row
     */
    private String mergeRow(List<String> cells) {

        if (cells.size() == 1) {
            return cells.get(0);
        }

        StringBuilder name = new StringBuilder();
        String qty = null;
        String price = null;
        String total = null;

        for (String cell : cells) {

            String cleaned = cleanNumber(cell);

            if (isNumeric(cleaned)) {

                if (qty == null) {
                    qty = cleaned;
                } else if (price == null) {
                    price = cleaned;
                } else if (total == null) {
                    total = cleaned;
                }

            } else {

                if (name.length() > 0) {
                    name.append(" ");
                }

                name.append(cell);
            }
        }

        StringBuilder row = new StringBuilder();

        if (name.length() > 0) {
            row.append(name);
        }

        if (qty != null) {
            row.append(" ").append(qty);
        }

        if (price != null) {
            row.append(" ").append(price);
        }

        if (total != null) {
            row.append(" ").append(total);
        }

        return row.toString().trim();
    }

    /**
     * Detect numeric value
     */
    private boolean isNumeric(String text) {

        if (text == null) {
            return false;
        }

        return NUMERIC_PATTERN.matcher(text).matches();
    }

    /**
     * Normalize OCR text
     */
    private String normalize(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Clean OCR number issues
     */
    private String cleanNumber(String value) {

        if (value == null) {
            return "";
        }

        String v = value
                .replace("$", "")
                .replace(",", ".")
                .replaceAll("\\s+", "");

        return v;
    }
}
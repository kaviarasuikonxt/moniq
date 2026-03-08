package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class OcrRowTextComposer {

    public String composeDocumentText(List<OcrRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (OcrRow row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }

            String line = normalizeWhitespace(row.joinTextsWithSpace());
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        return String.join("\n", lines).trim();
    }

    private String normalizeWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
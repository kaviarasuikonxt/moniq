package com.moniq.api.parsing;

import org.springframework.stereotype.Service;

@Service
public class ReceiptLineNormalizer {

    public String normalize(String line) {

        if (line == null) {
            return "";
        }

        String value = line.toLowerCase();

       // fix OCR numbers like "6. 90" -> "6.90"
    value = value.replaceAll("(\\d)\\.\\s+(\\d)", "$1.$2");

    // fix numbers like "1. 000" -> "1.000"
    value = value.replaceAll("(\\d)\\.\\s+(\\d{3})", "$1.$2");

    value = value.toLowerCase();

    value = value.replaceAll("[^a-z0-9.\\s']", " ");

    value = value.replaceAll("\\s+", " ").trim();

        return value;
    }
}
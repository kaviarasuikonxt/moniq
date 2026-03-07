package com.moniq.api.parsing;

import org.springframework.stereotype.Service;

@Service
public class ReceiptLineNormalizer {

    public String normalize(String line) {

        if (line == null) {
            return "";
        }

        String value = line.toLowerCase();

        // remove price
        value = value.replaceAll("\\d+\\.\\d{2}$", "");

        // remove quantity prefix like 2x
        value = value.replaceAll("^\\d+x\\s*", "");

        // remove special characters
        value = value.replaceAll("[^a-zA-Z\\s]", " ");

        // remove extra spaces
        value = value.replaceAll("\\s+", " ").trim();

        return value;
    }
}
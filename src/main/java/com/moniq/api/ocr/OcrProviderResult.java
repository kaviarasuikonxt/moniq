// src/main/java/com/moniq/api/ocr/OcrProviderResult.java
package com.moniq.api.ocr;

public class OcrProviderResult {
    private final String rawText;
    private final String normalizedJson;

    public OcrProviderResult(String rawText, String normalizedJson) {
        this.rawText = rawText;
        this.normalizedJson = normalizedJson;
    }

    public String getRawText() { return rawText; }
    public String getNormalizedJson() { return normalizedJson; }
}
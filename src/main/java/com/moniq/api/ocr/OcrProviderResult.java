package com.moniq.api.ocr;

import java.util.Objects;

public class OcrProviderResult {

    private final String rawText;
    private final String normalizedJson;
    private final String providerRawResponse;
    private final String status;

    public OcrProviderResult(String rawText, String normalizedJson) {
        this(rawText, normalizedJson, normalizedJson, "SUCCEEDED");
    }

    public OcrProviderResult(
            String rawText,
            String normalizedJson,
            String providerRawResponse,
            String status
    ) {
        this.rawText = defaultString(rawText);
        this.normalizedJson = defaultJson(normalizedJson);
        this.providerRawResponse = defaultJson(providerRawResponse);
        this.status = safeStatus(status);
    }

    public String getRawText() {
        return rawText;
    }

    public String getNormalizedJson() {
        return normalizedJson;
    }

    public String getProviderRawResponse() {
        return providerRawResponse;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasRawText() {
        return rawText != null && !rawText.isBlank();
    }

    public boolean hasNormalizedJson() {
        return normalizedJson != null && !normalizedJson.isBlank() && !"{}".equals(normalizedJson.trim());
    }

    public boolean hasProviderRawResponse() {
        return providerRawResponse != null && !providerRawResponse.isBlank() && !"{}".equals(providerRawResponse.trim());
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value;
    }

    private static String safeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return status.trim().toUpperCase();
    }

    @Override
    public String toString() {
        return "OcrProviderResult{" +
                "rawTextLength=" + (rawText == null ? 0 : rawText.length()) +
                ", normalizedJsonLength=" + (normalizedJson == null ? 0 : normalizedJson.length()) +
                ", providerRawResponseLength=" + (providerRawResponse == null ? 0 : providerRawResponse.length()) +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrProviderResult that)) return false;
        return Objects.equals(rawText, that.rawText)
                && Objects.equals(normalizedJson, that.normalizedJson)
                && Objects.equals(providerRawResponse, that.providerRawResponse)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawText, normalizedJson, providerRawResponse, status);
    }
}
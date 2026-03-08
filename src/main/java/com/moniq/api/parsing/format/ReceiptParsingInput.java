package com.moniq.api.parsing.format;

public class ReceiptParsingInput {

    private final String rawText;
    private final String layoutText;
    private final String normalizedJson;

    public ReceiptParsingInput(String rawText, String layoutText, String normalizedJson) {
        this.rawText = rawText == null ? "" : rawText;
        this.layoutText = layoutText == null ? "" : layoutText;
        this.normalizedJson = normalizedJson == null ? "" : normalizedJson;
    }

    public String getRawText() {
        return rawText;
    }

    public String getLayoutText() {
        return layoutText;
    }

    public String getNormalizedJson() {
        return normalizedJson;
    }
}
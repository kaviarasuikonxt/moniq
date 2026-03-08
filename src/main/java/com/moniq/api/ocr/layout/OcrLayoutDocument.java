package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OcrLayoutDocument {

    private final String provider;
    private final String version;
    private final String status;
    private final List<OcrLayoutPage> pages;

    public OcrLayoutDocument(String provider, String version, String status, List<OcrLayoutPage> pages) {
        this.provider = provider == null ? "" : provider.trim();
        this.version = version == null ? "" : version.trim();
        this.status = status == null ? "" : status.trim();
        this.pages = pages == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(pages));
    }

    public static OcrLayoutDocument empty() {
        return new OcrLayoutDocument("", "", "", List.of());
    }

    public String getProvider() {
        return provider;
    }

    public String getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public List<OcrLayoutPage> getPages() {
        return pages;
    }

    public boolean hasPages() {
        return pages != null && !pages.isEmpty();
    }

    public List<OcrLayoutLine> getAllLines() {
        List<OcrLayoutLine> result = new ArrayList<>();
        if (pages == null || pages.isEmpty()) {
            return result;
        }

        for (OcrLayoutPage page : pages) {
            if (page != null && page.getLines() != null && !page.getLines().isEmpty()) {
                result.addAll(page.getLines());
            }
        }
        return result;
    }

    public int totalLineCount() {
        return getAllLines().size();
    }

    @Override
    public String toString() {
        return "OcrLayoutDocument{" +
                "provider='" + provider + '\'' +
                ", version='" + version + '\'' +
                ", status='" + status + '\'' +
                ", pageCount=" + (pages == null ? 0 : pages.size()) +
                ", lineCount=" + totalLineCount() +
                '}';
    }
}
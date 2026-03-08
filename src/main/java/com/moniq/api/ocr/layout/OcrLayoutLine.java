package com.moniq.api.ocr.layout;

public class OcrLayoutLine {

    private final String text;
    private final OcrBoundingBox boundingBox;

    public OcrLayoutLine(String text, OcrBoundingBox boundingBox) {
        this.text = text == null ? "" : text.trim();
        this.boundingBox = boundingBox == null ? new OcrBoundingBox(null) : boundingBox;
    }

    public String getText() {
        return text;
    }

    public OcrBoundingBox getBoundingBox() {
        return boundingBox;
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public double centerY() {
        return boundingBox.centerY();
    }

    public double centerX() {
        return boundingBox.centerX();
    }

    public double minY() {
        return boundingBox.minY();
    }

    public double maxY() {
        return boundingBox.maxY();
    }

    public double minX() {
        return boundingBox.minX();
    }

    public double maxX() {
        return boundingBox.maxX();
    }

    public double height() {
        return boundingBox.height();
    }

    @Override
    public String toString() {
        return "OcrLayoutLine{" +
                "text='" + text + '\'' +
                ", boundingBox=" + boundingBox +
                '}';
    }
}
package com.moniq.api.ocr.layout;

public class OcrCell {

    private final String text;
    private final OcrBoundingBox boundingBox;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double centerX;
    private final double centerY;

    public OcrCell(String text, OcrBoundingBox boundingBox) {
        this.text = text == null ? "" : text.trim();
        this.boundingBox = boundingBox == null ? new OcrBoundingBox(null) : boundingBox;
        this.minX = this.boundingBox.minX();
        this.maxX = this.boundingBox.maxX();
        this.minY = this.boundingBox.minY();
        this.maxY = this.boundingBox.maxY();
        this.centerX = this.boundingBox.centerX();
        this.centerY = this.boundingBox.centerY();
    }

    public static OcrCell fromLine(OcrLayoutLine line) {
        if (line == null) {
            return new OcrCell("", new OcrBoundingBox(null));
        }
        return new OcrCell(line.getText(), line.getBoundingBox());
    }

    public String getText() {
        return text;
    }

    public OcrBoundingBox getBoundingBox() {
        return boundingBox;
    }

    public double getMinX() {
        return minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getWidth() {
        return Math.max(0.0d, maxX - minX);
    }

    public double getHeight() {
        return Math.max(0.0d, maxY - minY);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    @Override
    public String toString() {
        return "OcrCell{" +
                "text='" + text + '\'' +
                ", minX=" + minX +
                ", maxX=" + maxX +
                ", minY=" + minY +
                ", maxY=" + maxY +
                '}';
    }
}
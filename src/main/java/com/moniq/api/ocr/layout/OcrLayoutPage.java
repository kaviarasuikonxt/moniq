package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OcrLayoutPage {

    private final int pageNumber;
    private final double width;
    private final double height;
    private final String unit;
    private final List<OcrLayoutLine> lines;

    public OcrLayoutPage(int pageNumber, double width, double height, String unit, List<OcrLayoutLine> lines) {
        this.pageNumber = pageNumber;
        this.width = width;
        this.height = height;
        this.unit = unit == null ? "" : unit.trim();
        this.lines = lines == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public String getUnit() {
        return unit;
    }

    public List<OcrLayoutLine> getLines() {
        return lines;
    }

    public boolean hasLines() {
        return lines != null && !lines.isEmpty();
    }

    @Override
    public String toString() {
        return "OcrLayoutPage{" +
                "pageNumber=" + pageNumber +
                ", width=" + width +
                ", height=" + height +
                ", unit='" + unit + '\'' +
                ", lineCount=" + (lines == null ? 0 : lines.size()) +
                '}';
    }
}
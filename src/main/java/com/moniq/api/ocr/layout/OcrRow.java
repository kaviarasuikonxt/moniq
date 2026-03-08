package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OcrRow {

    private final List<OcrCell> cells = new ArrayList<>();

    public void addCell(OcrCell cell) {
        if (cell == null || !cell.hasText()) {
            return;
        }
        cells.add(cell);
        cells.sort(Comparator.comparingDouble(OcrCell::getMinX));
    }

    public List<OcrCell> getCells() {
        return Collections.unmodifiableList(cells);
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    public double getMinY() {
        if (cells.isEmpty()) {
            return 0.0d;
        }
        double min = Double.MAX_VALUE;
        for (OcrCell cell : cells) {
            min = Math.min(min, cell.getMinY());
        }
        return min == Double.MAX_VALUE ? 0.0d : min;
    }

    public double getMaxY() {
        if (cells.isEmpty()) {
            return 0.0d;
        }
        double max = Double.MIN_VALUE;
        for (OcrCell cell : cells) {
            max = Math.max(max, cell.getMaxY());
        }
        return max == Double.MIN_VALUE ? 0.0d : max;
    }

    public double getCenterY() {
        if (cells.isEmpty()) {
            return 0.0d;
        }
        return (getMinY() + getMaxY()) / 2.0d;
    }

    public double getAverageHeight() {
        if (cells.isEmpty()) {
            return 0.0d;
        }
        double total = 0.0d;
        for (OcrCell cell : cells) {
            total += cell.getHeight();
        }
        return total / cells.size();
    }

    public String joinTextsWithSpace() {
        return cells.stream()
                .map(OcrCell::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    public List<String> getTexts() {
        return cells.stream()
                .map(OcrCell::getText)
                .toList();
    }

    public boolean verticallyMatches(OcrCell candidate, double tolerance) {
        if (candidate == null || cells.isEmpty()) {
            return false;
        }

        double rowCenterY = getCenterY();
        double candidateCenterY = candidate.getCenterY();
        if (Math.abs(rowCenterY - candidateCenterY) <= tolerance) {
            return true;
        }

        double overlap = calculateVerticalOverlap(candidate);
        return overlap > 0.0d;
    }

    private double calculateVerticalOverlap(OcrCell candidate) {
        double overlapStart = Math.max(getMinY(), candidate.getMinY());
        double overlapEnd = Math.min(getMaxY(), candidate.getMaxY());
        return Math.max(0.0d, overlapEnd - overlapStart);
    }

    @Override
    public String toString() {
        return "OcrRow{" +
                "cells=" + cells.size() +
                ", centerY=" + getCenterY() +
                ", text='" + joinTextsWithSpace() + '\'' +
                '}';
    }
}
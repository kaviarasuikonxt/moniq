package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OcrBoundingBox {

    private final List<Double> points;

    public OcrBoundingBox(List<Double> points) {
        if (points == null) {
            this.points = List.of();
        } else {
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
        }
    }

    public List<Double> getPoints() {
        return points;
    }

    public boolean isEmpty() {
        return points == null || points.isEmpty();
    }

    public boolean isValidPolygon() {
        return points != null && points.size() >= 8 && points.size() % 2 == 0;
    }

    public double minX() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        double min = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i += 2) {
            min = Math.min(min, points.get(i));
        }
        return min == Double.MAX_VALUE ? 0.0d : min;
    }

    public double maxX() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        double max = Double.MIN_VALUE;
        for (int i = 0; i < points.size(); i += 2) {
            max = Math.max(max, points.get(i));
        }
        return max == Double.MIN_VALUE ? 0.0d : max;
    }

    public double minY() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        double min = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i += 2) {
            min = Math.min(min, points.get(i));
        }
        return min == Double.MAX_VALUE ? 0.0d : min;
    }

    public double maxY() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        double max = Double.MIN_VALUE;
        for (int i = 1; i < points.size(); i += 2) {
            max = Math.max(max, points.get(i));
        }
        return max == Double.MIN_VALUE ? 0.0d : max;
    }

    public double centerX() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        return (minX() + maxX()) / 2.0d;
    }

    public double centerY() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        return (minY() + maxY()) / 2.0d;
    }

    public double height() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        return Math.max(0.0d, maxY() - minY());
    }

    public double width() {
        if (!isValidPolygon()) {
            return 0.0d;
        }
        return Math.max(0.0d, maxX() - minX());
    }

    @Override
    public String toString() {
        return "OcrBoundingBox{" +
                "points=" + points +
                '}';
    }
}
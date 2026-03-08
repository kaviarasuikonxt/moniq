package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moniq.api.web.RequestCorrelation;

@Component
public class OcrLineRowGrouper {

    private static final Logger log = LoggerFactory.getLogger(OcrLineRowGrouper.class);

    private static final double DEFAULT_MIN_TOLERANCE = 8.0d;
    private static final double DEFAULT_HEIGHT_MULTIPLIER = 0.60d;

    public List<OcrRow> groupDocument(OcrLayoutDocument document) {
        if (document == null || !document.hasPages()) {
            log.warn("[{}] OCR row grouping skipped reason=empty_document",
                    RequestCorrelation.getRequestId());
            return List.of();
        }

        List<OcrRow> rows = new ArrayList<>();
        for (OcrLayoutPage page : document.getPages()) {
            rows.addAll(groupPage(page));
        }

        rows.sort(Comparator.comparingDouble(OcrRow::getMinY));

        log.info("[{}] OCR row grouping completed pages={} rows={}",
                RequestCorrelation.getRequestId(),
                document.getPages().size(),
                rows.size());

        return rows;
    }

    public List<OcrRow> groupPage(OcrLayoutPage page) {
        if (page == null || !page.hasLines()) {
            log.warn("[{}] OCR row grouping skipped page reason=no_lines pageNumber={}",
                    RequestCorrelation.getRequestId(),
                    page == null ? 0 : page.getPageNumber());
            return List.of();
        }

        List<OcrLayoutLine> sortedLines = new ArrayList<>(page.getLines());
        sortedLines.sort(Comparator
                .comparingDouble(OcrLayoutLine::centerY)
                .thenComparingDouble(OcrLayoutLine::minX));

        List<OcrRow> rows = new ArrayList<>();

        for (OcrLayoutLine line : sortedLines) {
            if (line == null || !line.hasText()) {
                continue;
            }

            OcrCell candidate = OcrCell.fromLine(line);
            OcrRow targetRow = findMatchingRow(rows, candidate);

            if (targetRow == null) {
                OcrRow newRow = new OcrRow();
                newRow.addCell(candidate);
                rows.add(newRow);
            } else {
                targetRow.addCell(candidate);
            }
        }

        rows.sort(Comparator.comparingDouble(OcrRow::getMinY));

        log.info("[{}] OCR row grouping pageCompleted pageNumber={} sourceLines={} rows={}",
                RequestCorrelation.getRequestId(),
                page.getPageNumber(),
                page.getLines().size(),
                rows.size());

        return rows;
    }

    private OcrRow findMatchingRow(List<OcrRow> rows, OcrCell candidate) {
        OcrRow bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (OcrRow row : rows) {
            double tolerance = calculateTolerance(row, candidate);
            if (row.verticallyMatches(candidate, tolerance)) {
                double distance = Math.abs(row.getCenterY() - candidate.getCenterY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = row;
                }
            }
        }

        return bestMatch;
    }

    private double calculateTolerance(OcrRow row, OcrCell candidate) {
        double rowHeight = row.getAverageHeight();
        double candidateHeight = candidate.getHeight();
        double base = Math.max(rowHeight, candidateHeight) * DEFAULT_HEIGHT_MULTIPLIER;
        return Math.max(DEFAULT_MIN_TOLERANCE, base);
    }
}
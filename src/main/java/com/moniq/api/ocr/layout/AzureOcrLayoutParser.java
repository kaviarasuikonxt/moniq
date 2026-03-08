package com.moniq.api.ocr.layout;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moniq.api.web.RequestCorrelation;

@Component
public class AzureOcrLayoutParser {

    private static final Logger log = LoggerFactory.getLogger(AzureOcrLayoutParser.class);

    private final ObjectMapper objectMapper;

    public AzureOcrLayoutParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OcrLayoutDocument parse(String normalizedJson) {
        String requestId = RequestCorrelation.getRequestId();

        if (normalizedJson == null || normalizedJson.isBlank()) {
            log.warn("[{}] OCR layout parse skipped reason=empty_normalized_json", requestId);
            return OcrLayoutDocument.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(normalizedJson);

            String provider = root.path("provider").asText("");
            String version = root.path("version").asText("");
            String status = root.path("status").asText("");

            List<OcrLayoutPage> pages = new ArrayList<>();
            JsonNode pageNodes = root.path("pages");

            if (pageNodes.isArray()) {
                for (JsonNode pageNode : pageNodes) {
                    OcrLayoutPage page = toPage(pageNode);
                    pages.add(page);
                }
            }

            OcrLayoutDocument document = new OcrLayoutDocument(provider, version, status, pages);

            log.info("[{}] OCR layout parsed provider={} version={} pages={} lines={}",
                    requestId,
                    document.getProvider(),
                    document.getVersion(),
                    document.getPages().size(),
                    document.totalLineCount());

            return document;
        } catch (Exception ex) {
            log.error("[{}] OCR layout parse failed", requestId, ex);
            return OcrLayoutDocument.empty();
        }
    }

    private OcrLayoutPage toPage(JsonNode pageNode) {
        int pageNumber = pageNode.path("pageNumber").asInt(0);
        double width = pageNode.path("width").asDouble(0.0d);
        double height = pageNode.path("height").asDouble(0.0d);
        String unit = pageNode.path("unit").asText("");

        List<OcrLayoutLine> lines = new ArrayList<>();
        JsonNode lineNodes = pageNode.path("lines");

        if (lineNodes.isArray()) {
            for (JsonNode lineNode : lineNodes) {
                OcrLayoutLine line = toLine(lineNode);
                if (line.hasText()) {
                    lines.add(line);
                }
            }
        }

        return new OcrLayoutPage(pageNumber, width, height, unit, lines);
    }

    private OcrLayoutLine toLine(JsonNode lineNode) {
        String text = normalizeWhitespace(lineNode.path("text").asText(""));
        OcrBoundingBox boundingBox = toBoundingBox(lineNode.path("boundingBox"));
        return new OcrLayoutLine(text, boundingBox);
    }

    private OcrBoundingBox toBoundingBox(JsonNode boundingBoxNode) {
        List<Double> points = new ArrayList<>();

        if (boundingBoxNode != null && boundingBoxNode.isArray()) {
            for (JsonNode point : boundingBoxNode) {
                if (point != null && point.isNumber()) {
                    points.add(point.asDouble());
                }
            }
        }

        return new OcrBoundingBox(points);
    }

    private String normalizeWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
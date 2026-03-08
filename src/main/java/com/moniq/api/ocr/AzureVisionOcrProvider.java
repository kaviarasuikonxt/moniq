package com.moniq.api.ocr;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moniq.api.web.RequestCorrelation;

@Component
public class AzureVisionOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureVisionOcrProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String key;

    private final Duration pollDelay;
    private final Duration maxWait;

    public AzureVisionOcrProvider(
            ObjectMapper objectMapper,
            @Value("${azure.vision.endpoint:}") String endpoint,
            @Value("${azure.vision.key:}") String key,
            @Value("${app.ocr.vision.poll-delay-ms:800}") long pollDelayMs,
            @Value("${app.ocr.vision.max-wait-ms:30000}") long maxWaitMs
    ) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint != null ? endpoint.trim() : "";
        this.key = key != null ? key.trim() : "";
        this.pollDelay = Duration.ofMillis(pollDelayMs);
        this.maxWait = Duration.ofMillis(maxWaitMs);

        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();
    }

    @Override
    public OcrProviderResult read(InputStream content, String contentType) {
        if (endpoint.isBlank() || key.isBlank()) {
            throw new IllegalStateException("Azure Vision endpoint/key not configured");
        }

        String requestId = RequestCorrelation.getRequestId();
        String url = normalizeEndpoint(endpoint) + "/vision/v3.2/read/analyze";

        try {
            byte[] body = content.readAllBytes();

            log.info("[{}] Azure Vision OCR request started provider={} contentType={} payloadBytes={}",
                    requestId, providerName(), sanitizeContentType(contentType), body.length);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", key);
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setAccept(MediaType.parseMediaTypes("application/json"));

            ResponseEntity<Void> resp = restClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            String operationLocation = resp.getHeaders().getFirst("Operation-Location");
            if (operationLocation == null || operationLocation.isBlank()) {
                log.error("[{}] Azure Vision OCR missing operation location", requestId);
                throw new IllegalStateException("Azure Vision missing Operation-Location header");
            }

            log.info("[{}] Azure Vision OCR started provider={} operationLocationReceived=true",
                    requestId, providerName());

            JsonNode finalJson = poll(operationLocation);
            String rawText = extractPlainText(finalJson);
            String providerRawResponse = safeWriteJson(finalJson);
            String normalizedJson = buildNormalizedJson(finalJson);

            log.info("[{}] Azure Vision OCR completed provider={} rawTextLength={} normalizedJsonLength={} providerRawResponseLength={}",
                    requestId,
                    providerName(),
                    rawText.length(),
                    normalizedJson.length(),
                    providerRawResponse.length());

            return new OcrProviderResult(
                    rawText,
                    normalizedJson,
                    providerRawResponse,
                    "SUCCEEDED"
            );

        } catch (Exception e) {
            log.error("[{}] Azure Vision OCR failed provider={}", requestId, providerName(), e);
            throw new IllegalStateException("Azure Vision OCR failed", e);
        }
    }

    @Override
    public String providerName() {
        return "AZURE_VISION";
    }

    private JsonNode poll(String operationLocation) throws Exception {
        long start = System.currentTimeMillis();
        String requestId = RequestCorrelation.getRequestId();

        while (true) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", key);
            headers.setAccept(MediaType.parseMediaTypes("application/json"));

            ResponseEntity<String> resp = restClient.get()
                    .uri(operationLocation)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .toEntity(String.class);

            String json = resp.getBody() == null ? "{}" : resp.getBody();
            JsonNode node = objectMapper.readTree(json);

            String status = node.path("status").asText("");
            if ("succeeded".equalsIgnoreCase(status)) {
                return node;
            }

            if ("failed".equalsIgnoreCase(status)) {
                log.warn("[{}] Azure Vision OCR polling returned failed status", requestId);
                throw new IllegalStateException("Azure Vision OCR status=failed");
            }

            if (System.currentTimeMillis() - start > maxWait.toMillis()) {
                log.warn("[{}] Azure Vision OCR polling timed out afterMs={}", requestId, maxWait.toMillis());
                throw new IllegalStateException("Azure Vision OCR timed out after " + maxWait.toMillis() + "ms");
            }

            Thread.sleep(pollDelay.toMillis());
        }
    }

    /**
     * Day 10 Step 1:
     * Still extract plain text for backward compatibility with Day 9 parser.
     */
    private String extractPlainText(JsonNode node) {
        List<String> lines = new ArrayList<>();

        JsonNode readResults = node.path("analyzeResult").path("readResults");
        if (readResults.isArray()) {
            for (JsonNode page : readResults) {
                JsonNode pageLines = page.path("lines");
                if (pageLines.isArray()) {
                    for (JsonNode line : pageLines) {
                        String text = normalizeWhitespace(line.path("text").asText(""));
                        if (!text.isBlank()) {
                            lines.add(text);
                        }
                    }
                }
            }
        }

        return String.join("\n", lines).trim();
    }

    /**
     * Day 10 Step 1 normalized shape:
     * {
     *   "provider": "AZURE_VISION",
     *   "version": "day10-step1",
     *   "status": "...",
     *   "pages": [
     *     {
     *       "pageNumber": 1,
     *       "width": ...,
     *       "height": ...,
     *       "unit": "...",
     *       "lines": [
     *         {
     *           "text": "...",
     *           "boundingBox": [...]
     *         }
     *       ]
     *     }
     *   ]
     * }
     */
    private String buildNormalizedJson(JsonNode source) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("provider", providerName());
            root.put("version", "day10-step1");
            root.put("status", source.path("status").asText("unknown"));

            ArrayNode pagesArray = root.putArray("pages");

            JsonNode readResults = source.path("analyzeResult").path("readResults");
            if (readResults.isArray()) {
                int fallbackPageNumber = 1;

                for (JsonNode page : readResults) {
                    ObjectNode normalizedPage = objectMapper.createObjectNode();
                    normalizedPage.put("pageNumber", page.path("page").asInt(fallbackPageNumber));
                    normalizedPage.put("width", page.path("width").asDouble(0.0d));
                    normalizedPage.put("height", page.path("height").asDouble(0.0d));
                    normalizedPage.put("unit", page.path("unit").asText(""));

                    ArrayNode linesArray = normalizedPage.putArray("lines");
                    JsonNode lineNodes = page.path("lines");

                    if (lineNodes.isArray()) {
                        for (JsonNode line : lineNodes) {
                            ObjectNode normalizedLine = objectMapper.createObjectNode();
                            normalizedLine.put("text", normalizeWhitespace(line.path("text").asText("")));
                            normalizedLine.set("boundingBox", normalizeBoundingBox(line));
                            linesArray.add(normalizedLine);
                        }
                    }

                    pagesArray.add(normalizedPage);
                    fallbackPageNumber++;
                }
            }

            return safeWriteJson(root);
        } catch (Exception ex) {
            log.warn("[{}] Azure Vision normalizedJson build skipped reason={}",
                    RequestCorrelation.getRequestId(), ex.getMessage());
            return "{}";
        }
    }

    private ArrayNode normalizeBoundingBox(JsonNode lineNode) {
        ArrayNode result = objectMapper.createArrayNode();

        JsonNode boundingBox = lineNode.path("boundingBox");
        if (boundingBox.isArray()) {
            for (JsonNode point : boundingBox) {
                result.add(point.asDouble());
            }
            return result;
        }

        JsonNode polygon = lineNode.path("polygon");
        if (polygon.isArray()) {
            for (JsonNode point : polygon) {
                result.add(point.asDouble());
            }
        }

        return result;
    }

    private String safeWriteJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            log.warn("[{}] OCR JSON serialization fallback triggered reason={}",
                    RequestCorrelation.getRequestId(), ex.getMessage());
            return "{}";
        }
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

    private String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "unknown";
        }
        return contentType.trim();
    }

    private static String normalizeEndpoint(String ep) {
        String e = ep.trim();
        if (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        return e;
    }
}
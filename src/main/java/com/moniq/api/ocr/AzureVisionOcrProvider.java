// src/main/java/com/moniq/api/ocr/AzureVisionOcrProvider.java
package com.moniq.api.ocr;

import java.io.InputStream;
import java.time.Duration;

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

        String url = normalizeEndpoint(endpoint) + "/vision/v3.2/read/analyze";

        try {
            byte[] body = content.readAllBytes();

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
                throw new IllegalStateException("Azure Vision missing Operation-Location header");
            }

            log.info("[{}] Azure Vision OCR started operation={}",
                RequestCorrelation.getRequestId(), operationLocation);

            JsonNode finalJson = poll(operationLocation);
            String rawText = extractPlainText(finalJson);

            // Store "normalized JSON" (what Azure returned) as JSON string
            String normalizedJson = objectMapper.writeValueAsString(finalJson);

            return new OcrProviderResult(rawText, normalizedJson);

        } catch (Exception e) {
            throw new IllegalStateException("Azure Vision OCR failed", e);
        }
    }

    @Override
    public String providerName() {
        return "AZURE_VISION";
    }

    private JsonNode poll(String operationLocation) throws Exception {
        long start = System.currentTimeMillis();

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
            if ("succeeded".equalsIgnoreCase(status)) return node;
            if ("failed".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Azure Vision OCR status=failed: " + json);
            }

            if (System.currentTimeMillis() - start > maxWait.toMillis()) {
                throw new IllegalStateException("Azure Vision OCR timed out after " + maxWait.toMillis() + "ms");
            }

            Thread.sleep(pollDelay.toMillis());
        }
    }

    /**
     * Extract plain text from Azure Read results:
     * analyzeResult -> readResults[] -> lines[] -> text
     */
    private String extractPlainText(JsonNode node) {
        StringBuilder sb = new StringBuilder();

        JsonNode readResults = node.path("analyzeResult").path("readResults");
        if (readResults.isArray()) {
            for (JsonNode page : readResults) {
                JsonNode lines = page.path("lines");
                if (lines.isArray()) {
                    for (JsonNode line : lines) {
                        String text = line.path("text").asText("");
                        if (!text.isBlank()) {
                            sb.append(text).append('\n');
                        }
                    }
                }
            }
        }

        String out = sb.toString().trim();
        return out.isBlank() ? "" : out;
    }

    private static String normalizeEndpoint(String ep) {
        String e = ep.trim();
        if (e.endsWith("/")) e = e.substring(0, e.length() - 1);
        return e;
    }
}
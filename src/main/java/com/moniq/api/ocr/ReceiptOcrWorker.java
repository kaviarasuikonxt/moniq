// src/main/java/com/moniq/api/ocr/ReceiptOcrWorker.java
package com.moniq.api.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moniq.api.web.RequestCorrelation;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class ReceiptOcrWorker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrWorker.class);

    private final ObjectProvider<QueueClient> queueClientProvider;
    private final ObjectMapper objectMapper;
    private final ReceiptOcrWorkerProcessor processor;

    private final boolean enabled;
    private final long pollIntervalMs;
    private final int visibilityTimeoutSeconds;

    public ReceiptOcrWorker(
            ObjectProvider<QueueClient> queueClientProvider,
            ObjectMapper objectMapper,
            ReceiptOcrWorkerProcessor processor,
            @Value("${app.ocr.worker.enabled:false}") boolean enabled,
            @Value("${app.ocr.worker.poll-interval-ms:2000}") long pollIntervalMs,
            @Value("${app.ocr.worker.visibility-timeout-seconds:60}") int visibilityTimeoutSeconds) {
        this.queueClientProvider = queueClientProvider;
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.enabled = enabled;
        this.pollIntervalMs = pollIntervalMs;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("ReceiptOcrWorker disabled (app.ocr.worker.enabled=false)");
            return;
        }

        QueueClient queueClient = queueClientProvider.getIfAvailable();
        if (queueClient == null) {
            log.warn("ReceiptOcrWorker enabled but Azure Queue is not configured (storage.connection-string missing).");
            return;
        }

        queueClient.createIfNotExists();
        log.info("ReceiptOcrWorker started pollIntervalMs={} visibilityTimeoutSeconds={}",
                pollIntervalMs, visibilityTimeoutSeconds);

        while (true) {
            try {
                /*List<QueueMessageItem> messages = queueClient.receiveMessages(
                        1,
                        Duration.ofSeconds(visibilityTimeoutSeconds),
                        null);*/

                Iterable<QueueMessageItem> iterable = queueClient.receiveMessages(
                        1,
                        Duration.ofSeconds(visibilityTimeoutSeconds),
                        Duration.ofSeconds(5),
                        com.azure.core.util.Context.NONE);

                var it = iterable.iterator();
                if (!it.hasNext()) {
                    Thread.sleep(pollIntervalMs);
                    continue;
                }
                QueueMessageItem msg = it.next();

                if (msg == null) {
                    Thread.sleep(pollIntervalMs);
                    continue;
                }

                RequestCorrelation.setRequestId("ocr-" + UUID.randomUUID());

                OcrJobMessage job = objectMapper.readValue(msg.getBody().toString(), OcrJobMessage.class);

                log.info("[{}] Dequeued OCR job receiptId={} userId={} blobName={}",
                        RequestCorrelation.getRequestId(), job.getReceiptId(), job.getUserId(), job.getBlobName());

                processor.process(job);

                queueClient.deleteMessage(msg.getMessageId(), msg.getPopReceipt());
                log.info("[{}] OCR job done receiptId={} (message deleted)",
                        RequestCorrelation.getRequestId(), job.getReceiptId());

            } catch (Exception e) {
                log.error("Worker loop error: {}", e.getMessage(), e);
                sleepQuietly(pollIntervalMs);
            } finally {
                RequestCorrelation.clear();
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
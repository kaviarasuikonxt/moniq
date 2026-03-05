// src/main/java/com/moniq/api/ocr/ReceiptOcrQueueService.java
package com.moniq.api.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moniq.api.web.RequestCorrelation;
import com.azure.storage.queue.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ReceiptOcrQueueService {
    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrQueueService.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public ReceiptOcrQueueService(QueueClient queueClient, ObjectMapper objectMapper) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
    }

    public void enqueue(UUID receiptId, UUID userId, String blobName, String contentType) {
        try {
            OcrJobMessage msg = new OcrJobMessage();
            msg.setReceiptId(receiptId);
            msg.setUserId(userId);
            msg.setBlobName(blobName);
            msg.setContentType(contentType);
            msg.setCreatedAt(OffsetDateTime.now());

            String payload = objectMapper.writeValueAsString(msg);

            queueClient.createIfNotExists();
            queueClient.sendMessage(payload);

            log.info("[{}] Enqueued OCR job receiptId={} userId={} blobName={}",
                RequestCorrelation.getRequestId(), receiptId, userId, blobName
            );
        } catch (Exception e) {
            log.error("[{}] Failed to enqueue OCR job receiptId={} userId={} blobName={}",
                RequestCorrelation.getRequestId(), receiptId, userId, blobName, e
            );
            throw new IllegalStateException("Failed to enqueue OCR job");
        }
    }
}
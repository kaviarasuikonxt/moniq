// src/main/java/com/moniq/api/ocr/ReceiptOcrWorkerProcessor.java
package com.moniq.api.ocr;

import java.io.InputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moniq.api.web.RequestCorrelation;

/**
 * IMPORTANT: This class touches receipt + blob storage.
 * We keep it separate to avoid making ReceiptOcrWorker huge.
 */
@Component
public class ReceiptOcrWorkerProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrWorkerProcessor.class);

    private final ReceiptOcrWorkerReceiptFacade receiptFacade;
    private final OcrService ocrService;

    public ReceiptOcrWorkerProcessor(ReceiptOcrWorkerReceiptFacade receiptFacade, OcrService ocrService) {
        this.receiptFacade = receiptFacade;
        this.ocrService = ocrService;
    }

    public void process(OcrJobMessage job) {
        UUID receiptId = job.getReceiptId();

        // Idempotency: if already completed, skip.
        if (receiptFacade.isOcrCompleted(receiptId)) {
            log.info("[{}] Skip OCR receiptId={} already OCR_COMPLETED", RequestCorrelation.getRequestId(), receiptId);
            return;
        }

        try (InputStream is = receiptFacade.openBlobStream(job.getBlobName())) {
            receiptFacade.markOcrRunning(receiptId); // sets OCR_PENDING (safe to set again)

            ocrService.runOcrAndPersist(receiptId, is, job.getContentType());

            receiptFacade.markOcrCompleted(receiptId);

        } catch (Exception e) {
            log.error("[{}] OCR failed receiptId={} err={}",
                RequestCorrelation.getRequestId(), receiptId, e.getMessage(), e);
            receiptFacade.markOcrFailed(receiptId, truncate(e.getMessage(), 500));
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "OCR failed";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
// src/main/java/com/moniq/api/ocr/ReceiptOcrWorkerReceiptFacade.java
package com.moniq.api.ocr;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.moniq.api.receipt.ReceiptEntity;
import com.moniq.api.receipt.ReceiptRepository;
import com.moniq.api.receipt.ReceiptStatus;
import com.moniq.api.storage.BlobStorageService;

@Component
public class ReceiptOcrWorkerReceiptFacade {

    private final ReceiptRepository receiptRepository;
    private final BlobStorageService blobStorageService;

    public ReceiptOcrWorkerReceiptFacade(ReceiptRepository receiptRepository, BlobStorageService blobStorageService) {
        this.receiptRepository = receiptRepository;
        this.blobStorageService = blobStorageService;
    }

    public InputStream openBlobStream(String blobName) {
        return blobStorageService.openStream(blobName);
    }

    public boolean isOcrCompleted(UUID receiptId) {
        return receiptRepository.findById(receiptId)
            .map(r -> "OCR_COMPLETED".equals(String.valueOf(r.getStatus())))
            .orElse(false);
    }

    @Transactional
    public void markOcrRunning(UUID receiptId) {
        ReceiptEntity r = receiptRepository.findById(receiptId).orElseThrow();
        r.setStatus(ReceiptStatus.OCR_PENDING);
        receiptRepository.save(r);
    }

    @Transactional
    public void markOcrCompleted(UUID receiptId) {
        ReceiptEntity r = receiptRepository.findById(receiptId).orElseThrow();
        r.setStatus(ReceiptStatus.OCR_COMPLETED);
      //  r.setOcrErrorMessage(null);
        receiptRepository.save(r);
    }

    @Transactional
    public void markOcrFailed(UUID receiptId, String error) {
        ReceiptEntity r = receiptRepository.findById(receiptId).orElseThrow();
        r.setStatus(ReceiptStatus.FAILED);
     //   r.setOcrErrorMessage(error);
        receiptRepository.save(r);
    }
}
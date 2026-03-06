// src/main/java/com/moniq/api/ocr/ReceiptOcrWorkerReceiptFacade.java
package com.moniq.api.ocr;

import com.moniq.api.receipt.ReceiptEntity;
import com.moniq.api.receipt.ReceiptRepository;
import com.moniq.api.receipt.ReceiptStatus;
import com.moniq.api.storage.BlobStorageService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReceiptOcrWorkerReceiptFacade {

    private final ReceiptRepository receiptRepository;
    private final BlobStorageService blobStorageService;

    public ReceiptOcrWorkerReceiptFacade(ReceiptRepository receiptRepository,
                                         BlobStorageService blobStorageService) {
        this.receiptRepository = receiptRepository;
        this.blobStorageService = blobStorageService;
    }

    public InputStream openBlobStream(String blobName) {
        return blobStorageService.openStream(blobName);
    }

    public boolean isOcrCompleted(UUID receiptId) {
        return receiptRepository.findById(receiptId)
                .map(r -> r.getStatus() == ReceiptStatus.OCR_COMPLETED)
                .orElse(false);
    }

    @Transactional
    public boolean markOcrRunning(UUID receiptId) {
        Optional<ReceiptEntity> opt = receiptRepository.findById(receiptId);
        if (opt.isEmpty()) {
            return false;
        }

        ReceiptEntity r = opt.get();
        r.setStatus(ReceiptStatus.OCR_PENDING);
        receiptRepository.save(r);
        return true;
    }

    @Transactional
    public boolean markOcrCompleted(UUID receiptId) {
        Optional<ReceiptEntity> opt = receiptRepository.findById(receiptId);
        if (opt.isEmpty()) {
            return false;
        }

        ReceiptEntity r = opt.get();
        r.setStatus(ReceiptStatus.OCR_COMPLETED);
        receiptRepository.save(r);
        return true;
    }

    @Transactional
    public boolean markOcrFailed(UUID receiptId, String error) {
        Optional<ReceiptEntity> opt = receiptRepository.findById(receiptId);
        if (opt.isEmpty()) {
            return false;
        }

        ReceiptEntity r = opt.get();
        r.setStatus(ReceiptStatus.FAILED);
        receiptRepository.save(r);
        return true;
    }
}
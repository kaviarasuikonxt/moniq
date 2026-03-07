package com.moniq.api.receipt;

import com.moniq.api.ocr.repository.ReceiptItemRepository;
import com.moniq.api.receipt.dto.ReceiptCategorySummaryDTO;
import com.moniq.api.receipt.dto.ReceiptSummaryResponse;
import com.moniq.api.web.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReceiptSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptSummaryService.class);

    private final ReceiptItemRepository itemRepo;

    public ReceiptSummaryService(ReceiptItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    public ReceiptSummaryResponse getSummary(UUID receiptId) {

        List<ReceiptCategorySummaryDTO> categories =
                itemRepo.summarizeByCategory(receiptId);

        int totalItems = categories.stream()
                .mapToInt(c -> (int) c.getItems())
                .sum();

        log.info("[{}] Category summary generated receiptId={} categories={}",
                RequestCorrelation.getRequestId(), receiptId, categories.size());

        return new ReceiptSummaryResponse(receiptId, totalItems, categories);
    }
}
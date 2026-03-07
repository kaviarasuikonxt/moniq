package com.moniq.api.receipt.api;

import com.moniq.api.receipt.ReceiptSummaryService;
import com.moniq.api.receipt.dto.ReceiptSummaryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptSummaryController {

    private final ReceiptSummaryService summaryService;

    public ReceiptSummaryController(ReceiptSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/{id}/summary")
    public ReceiptSummaryResponse getSummary(@PathVariable("id") UUID receiptId) {
        return summaryService.getSummary(receiptId);
    }
}
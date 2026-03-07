package com.moniq.api.receipt.dto;

import java.util.List;
import java.util.UUID;

public class ReceiptSummaryResponse {

    private UUID receiptId;
    private int totalItems;
    private List<ReceiptCategorySummaryDTO> categories;

    public ReceiptSummaryResponse(UUID receiptId, int totalItems, List<ReceiptCategorySummaryDTO> categories) {
        this.receiptId = receiptId;
        this.totalItems = totalItems;
        this.categories = categories;
    }

    public UUID getReceiptId() {
        return receiptId;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public List<ReceiptCategorySummaryDTO> getCategories() {
        return categories;
    }
}
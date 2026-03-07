package com.moniq.api.receipt.dto;

import java.math.BigDecimal;

public class ReceiptCategorySummaryDTO {

    private String category;
    private long items;
    private BigDecimal totalAmount;

    public ReceiptCategorySummaryDTO(String category, long items, BigDecimal totalAmount) {
        this.category = category;
        this.items = items;
        this.totalAmount = totalAmount;
    }

    public String getCategory() {
        return category;
    }

    public long getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
// src/main/java/com/moniq/api/receipt/dto/ReceiptItemResponse.java
package com.moniq.api.receipt.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class ReceiptItemResponse {
    public UUID id;
    public int lineNo;
    public String rawLine;
    public String itemName;

    public BigDecimal quantity;
    public BigDecimal unitPrice;
    public BigDecimal amount;

    public String currency;
      public String category;
    public BigDecimal confidence;


    public ReceiptItemResponse() {
    }

    public ReceiptItemResponse(UUID id, int lineNo, String rawLine, String itemName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal amount, String currency, String category, BigDecimal confidence) {
        this.id = id;
        this.lineNo = lineNo;
        this.rawLine = rawLine;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.confidence = confidence;
   
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }


  
}
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
}
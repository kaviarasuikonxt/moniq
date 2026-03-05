// src/main/java/com/moniq/api/receipt/dto/ReceiptOcrResponse.java
package com.moniq.api.receipt.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ReceiptOcrResponse {
    public UUID receiptId;
    public String provider;
    public String rawText;
    public OffsetDateTime createdAt;
}
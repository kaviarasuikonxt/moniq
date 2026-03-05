// src/main/java/com/moniq/api/receipt/dto/ReceiptItemsResponse.java
package com.moniq.api.receipt.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReceiptItemsResponse {
    public UUID receiptId;
    public String status;
    public List<ReceiptItemResponse> items = new ArrayList<>();
}
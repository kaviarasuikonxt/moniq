// src/main/java/com/moniq/api/ocr/OcrJobMessage.java
package com.moniq.api.ocr;

import java.util.UUID;

public class OcrJobMessage {
    private UUID receiptId;
    private UUID userId;
    private String blobName;
    private String contentType;

  
    private String createdAt;

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getBlobName() { return blobName; }
    public void setBlobName(String blobName) { this.blobName = blobName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
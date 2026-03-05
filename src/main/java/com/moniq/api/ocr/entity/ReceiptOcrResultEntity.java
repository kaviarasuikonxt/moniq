// src/main/java/com/moniq/api/ocr/entity/ReceiptOcrResultEntity.java
package com.moniq.api.ocr.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_ocr_results")
public class ReceiptOcrResultEntity {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    /**
     * Store normalized OCR JSON as text, persisted to Postgres JSONB.
     * Keeps DB flexible without requiring special Hibernate JSON types.
     */
    @Column(name = "ocr_json", columnDefinition = "jsonb")
    private String ocrJson;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider = "AZURE_VISION";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (provider == null) provider = "AZURE_VISION";
    }

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getOcrJson() { return ocrJson; }
    public void setOcrJson(String ocrJson) { this.ocrJson = ocrJson; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
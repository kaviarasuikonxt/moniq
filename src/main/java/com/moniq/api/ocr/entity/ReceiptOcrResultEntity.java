package com.moniq.api.ocr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipt_ocr_results")
public class ReceiptOcrResultEntity {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    /**
     * Day 10 Step 1:
     * Keep existing DB column name for backward compatibility.
     * This field stores the normalized OCR JSON string.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_json", columnDefinition = "jsonb")
    private String ocrJson;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider = "AZURE_VISION";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (provider == null || provider.isBlank()) {
            provider = "AZURE_VISION";
        }
        if (rawText == null) {
            rawText = "";
        }
        if (ocrJson == null || ocrJson.isBlank()) {
            ocrJson = "{}";
        }
    }

    public UUID getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText == null ? "" : rawText;
    }

    public String getOcrJson() {
        return ocrJson;
    }

    public void setOcrJson(String ocrJson) {
        this.ocrJson = (ocrJson == null || ocrJson.isBlank()) ? "{}" : ocrJson;
    }

    /**
     * Alias to make Day 10 code easier to read while keeping existing DB field name.
     */
    public String getNormalizedJson() {
        return getOcrJson();
    }

    /**
     * Alias to make Day 10 code easier to read while keeping existing DB field name.
     */
    public void setNormalizedJson(String normalizedJson) {
        setOcrJson(normalizedJson);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = (provider == null || provider.isBlank()) ? "AZURE_VISION" : provider;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
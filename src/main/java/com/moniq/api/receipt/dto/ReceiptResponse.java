package com.moniq.api.receipt.dto;

import com.moniq.api.receipt.ReceiptStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReceiptResponse {

    private UUID id;
    private String fileUrl;
    private String fileName;
    private String contentType;
    private long fileSizeBytes;

    private String merchant;
    private OffsetDateTime receiptDate;
    private BigDecimal totalAmount;
    private String currency;

    private ReceiptStatus status;
    private OffsetDateTime createdAt;

    public static ReceiptResponse of(
            UUID id,
            String fileUrl,
            String fileName,
            String contentType,
            long fileSizeBytes,
            String merchant,
            OffsetDateTime receiptDate,
            BigDecimal totalAmount,
            String currency,
            ReceiptStatus status,
            OffsetDateTime createdAt
    ) {
        ReceiptResponse r = new ReceiptResponse();
        r.id = id;
        r.fileUrl = fileUrl;
        r.fileName = fileName;
        r.contentType = contentType;
        r.fileSizeBytes = fileSizeBytes;
        r.merchant = merchant;
        r.receiptDate = receiptDate;
        r.totalAmount = totalAmount;
        r.currency = currency;
        r.status = status;
        r.createdAt = createdAt;
        return r;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public OffsetDateTime getReceiptDate() { return receiptDate; }
    public void setReceiptDate(OffsetDateTime receiptDate) { this.receiptDate = receiptDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public ReceiptStatus getStatus() { return status; }
    public void setStatus(ReceiptStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
package com.moniq.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.receipts")
public class ReceiptUploadProperties {

    /**
     * Max file size (bytes)
     */
    private long maxFileBytes = 10 * 1024 * 1024; // 10MB default

    /**
     * If set, use it to build a public URL:
     * {publicBaseUrl}/{blobName}
     * Env: APP_RECEIPTS_PUBLIC_BASE_URL
     */
    private String publicBaseUrl;

    /**
     * SAS TTL minutes (only used when publicBaseUrl not set)
     */
    private int sasTtlMinutes = 60;

    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public int getSasTtlMinutes() { return sasTtlMinutes; }
    public void setSasTtlMinutes(int sasTtlMinutes) { this.sasTtlMinutes = sasTtlMinutes; }
}
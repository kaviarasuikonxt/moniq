package com.moniq.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Env: AZURE_STORAGE_CONNECTION_STRING
     */
    private String connectionString;

    /**
     * Env: AZURE_STORAGE_CONTAINER_RECEIPTS
     */
    private String containerReceipts = "receipts";
     // ✅ add this
    private String queueReceiptOcr = "receipt-ocr";

    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }

    public String getContainerReceipts() { return containerReceipts; }
    public void setContainerReceipts(String containerReceipts) { this.containerReceipts = containerReceipts; }

    public String getQueueReceiptOcr() { return queueReceiptOcr; }
    public void setQueueReceiptOcr(String queueReceiptOcr) { this.queueReceiptOcr = queueReceiptOcr; }

}
package com.moniq.api.storage;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;

@Service
@EnableConfigurationProperties(ReceiptUploadProperties.class)
public class BlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(BlobStorageService.class);
    private final ObjectProvider<BlobContainerClient> receiptsContainerProvider;
    private final ReceiptUploadProperties uploadProps;

    public BlobStorageService(ObjectProvider<BlobContainerClient> receiptsContainerProvider,
                              ReceiptUploadProperties uploadProps) {
        this.receiptsContainerProvider = receiptsContainerProvider;
        this.uploadProps = uploadProps;
    }

    public void upload(String blobName, InputStream data, long length, String contentType) {
        BlobContainerClient receiptsContainer = ensureConfigured();
        BlobClient blobClient = receiptsContainer.getBlobClient(blobName);
        blobClient.upload(data, length, true);
        blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
    }
 public void deleteIfExists(String blobName) {
    BlobContainerClient receiptsContainer = ensureConfigured();
    BlobClient blobClient = receiptsContainer.getBlobClient(blobName);
    blobClient.deleteIfExists();
}

    public String resolveFileUrl(String blobName) {
        BlobContainerClient receiptsContainer = ensureConfigured();

        String publicBase = uploadProps.getPublicBaseUrl();
        if (publicBase != null && !publicBase.isBlank()) {
            String normalized = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
            return normalized + "/" + urlEncodePath(blobName);
        }

        BlobClient blobClient = receiptsContainer.getBlobClient(blobName);
        int ttlMinutes = uploadProps.getSasTtlMinutes();

        BlobSasPermission perm = new BlobSasPermission().setReadPermission(true);
        OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(ttlMinutes);

        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiry, perm)
                .setProtocol(SasProtocol.HTTPS_ONLY);

        String sas = blobClient.generateSas(values);
        return blobClient.getBlobUrl() + "?" + sas;
    }

    /**
     * Day 8: Worker needs to read blob content as stream (no local file writes).
     * Caller should close the returned InputStream.
     */
    public InputStream openStream(String blobName) {
        BlobContainerClient receiptsContainer = ensureConfigured();
        BlobClient blobClient = receiptsContainer.getBlobClient(blobName);
        return blobClient.openInputStream();
    }

    private BlobContainerClient ensureConfigured() {
        BlobContainerClient client = receiptsContainerProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Azure storage is not configured. Set AZURE_STORAGE_CONNECTION_STRING.");
        }
        return client;
    }

    private String urlEncodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        log.info("Blob Path: {}", sb.toString());
        return sb.toString();
    }

   
   
}
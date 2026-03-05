package com.moniq.api.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Service
@EnableConfigurationProperties(ReceiptUploadProperties.class)
public class BlobStorageService {

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
        return sb.toString();
    }
}
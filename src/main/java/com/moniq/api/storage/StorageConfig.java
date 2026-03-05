package com.moniq.api.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {


    /*/
    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "connection-string")
    public BlobServiceClient blobServiceClient(StorageProperties props) {
      //  if (props.getConnectionString() == null || props.getConnectionString().isBlank()) {
      //      throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING is not set (storage.connection-string).");
      //  }
        return new BlobServiceClientBuilder()
                .connectionString(props.getConnectionString())
                .buildClient();
    }

    @Bean
     @ConditionalOnProperty(prefix = "storage", name = "connection-string")
    public BlobContainerClient receiptsContainerClient(BlobServiceClient blobServiceClient, StorageProperties props) {
        String container = props.getContainerReceipts();
      //  if (container == null || container.isBlank()) {
       //     throw new IllegalStateException("AZURE_STORAGE_CONTAINER_RECEIPTS is not set (storage.container-receipts).");
       // }
        BlobContainerClient client = blobServiceClient.getBlobContainerClient(container);
        if (!client.exists()) {
            client.create();
        }
        return client;
    }
        */

      @Bean
    @ConditionalOnProperty(prefix = "storage", name = "connection-string")
    public BlobServiceClient blobServiceClient(StorageProperties props) {
        // IMPORTANT: do NOT throw here for local mode
        return new BlobServiceClientBuilder()
                .connectionString(props.getConnectionString())
                .buildClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "connection-string")
    public BlobContainerClient receiptsContainerClient(BlobServiceClient blobServiceClient, StorageProperties props) {
        String container = props.getContainerReceipts();
        BlobContainerClient client = blobServiceClient.getBlobContainerClient(container);
        if (!client.exists()) {
            client.create();
        }
        return client;
    }
}
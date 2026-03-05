// src/main/java/com/moniq/api/ocr/OcrQueueConfig.java
package com.moniq.api.ocr;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNull;

@Configuration
public class OcrQueueConfig {

    @Bean
    public QueueClient receiptOcrQueueClient(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.queue.receipt-ocr:receipt-ocr}") String queueName
    ) {
        requireNonNull(connectionString, "azure.storage.connection-string is required");
        requireNonNull(queueName, "azure.storage.queue.receipt-ocr is required");

        return new QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .buildClient();
    }
}
// src/main/java/com/moniq/api/ocr/OcrQueueConfig.java
package com.moniq.api.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.moniq.api.storage.StorageProperties;

@Configuration
public class OcrQueueConfig {

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "connection-string")
public QueueClient receiptOcrQueueClient(StorageProperties props) {
        return new QueueClientBuilder()
                .connectionString(props.getConnectionString())
                .queueName(props.getQueueReceiptOcr())
                .buildClient();
    }
}
// src/main/java/com/moniq/api/ocr/repository/ReceiptItemRepository.java
package com.moniq.api.ocr.repository;

import com.moniq.api.ocr.entity.ReceiptItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItemEntity, UUID> {
    List<ReceiptItemEntity> findByReceiptIdOrderByLineNoAsc(UUID receiptId);
    void deleteByReceiptId(UUID receiptId);
}
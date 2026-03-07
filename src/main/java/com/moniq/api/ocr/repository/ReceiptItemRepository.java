// src/main/java/com/moniq/api/ocr/repository/ReceiptItemRepository.java
package com.moniq.api.ocr.repository;

import com.moniq.api.ocr.entity.ReceiptItemEntity;
import com.moniq.api.receipt.dto.ReceiptCategorySummaryDTO;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItemEntity, UUID> {
    List<ReceiptItemEntity> findByReceiptIdOrderByLineNoAsc(UUID receiptId);

    void deleteByReceiptId(UUID receiptId);

    @Query("""
            SELECT new com.moniq.api.receipt.dto.ReceiptCategorySummaryDTO(
                i.category,
                COUNT(i),
                SUM(i.amount)
            )
            FROM ReceiptItemEntity i
            WHERE i.receiptId = :receiptId
            GROUP BY i.category
            ORDER BY SUM(i.amount) DESC
            """)
    List<ReceiptCategorySummaryDTO> summarizeByCategory(UUID receiptId);
}
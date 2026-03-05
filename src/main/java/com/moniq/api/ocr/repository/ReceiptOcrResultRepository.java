// src/main/java/com/moniq/api/ocr/repository/ReceiptOcrResultRepository.java
package com.moniq.api.ocr.repository;

import com.moniq.api.ocr.entity.ReceiptOcrResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReceiptOcrResultRepository extends JpaRepository<ReceiptOcrResultEntity, UUID> {
}
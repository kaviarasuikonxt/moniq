package com.moniq.api.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<ReceiptEntity, UUID> {

    List<ReceiptEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ReceiptEntity> findByIdAndUserId(UUID id, UUID userId);
}
package com.moniq.api.receipt;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.moniq.api.auth.UserEntity;
import com.moniq.api.ocr.ReceiptOcrQueueService;
import com.moniq.api.receipt.dto.ReceiptResponse;
import com.moniq.api.repository.UserRepository;
import com.moniq.api.storage.BlobStorageService;
import com.moniq.api.storage.ReceiptUploadProperties;
import com.moniq.api.web.RequestCorrelation;

import jakarta.transaction.Transactional;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "image/png",
            "application/pdf",
            "application/x-pdf",
            "application/octet-stream");

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final BlobStorageService blobStorageService;
    private final ReceiptUploadProperties uploadProps;
    private final ReceiptOcrQueueService queueService;

    public ReceiptService(
            ReceiptRepository receiptRepository,
            UserRepository userRepository,
            BlobStorageService blobStorageService,
            ReceiptUploadProperties uploadProps,
            ReceiptOcrQueueService queueService) {
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.blobStorageService = blobStorageService;
        this.uploadProps = uploadProps;
        this.queueService = queueService;
    }

    public ReceiptResponse uploadReceipt(
            String userEmail,
            MultipartFile file,
            String merchant,
            OffsetDateTime receiptDate,
            BigDecimal totalAmount,
            String currency) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email"));

        validateFile(file);

        UUID receiptId = UUID.randomUUID();
        String originalName = safeFileName(Objects.requireNonNullElse(file.getOriginalFilename(), "receipt"));

        // NOTE: you currently get receipts/receipts/... because container is "receipts"
        // and blobName starts with "receipts/". We'll keep it unchanged for now.
        // String blobName = "receipts/" + user.getId() + "/" + receiptId + "/" +
        // originalName;
        String blobName = user.getId() + "/" + receiptId + "/" + originalName;
        log.info("Blob name for receipt upload: {}", blobName);

        String normalizedType = normalizeContentType(file.getContentType(), originalName);

        try (InputStream in = file.getInputStream()) {
            blobStorageService.upload(blobName, in, file.getSize(), normalizedType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload receipt to storage", e);
        }

        ReceiptEntity entity = new ReceiptEntity();
        entity.setId(receiptId);
        entity.setUserId(user.getId());
        entity.setMerchant(blankToNull(merchant));
        entity.setReceiptDate(receiptDate);
        entity.setTotalAmount(totalAmount);
        entity.setCurrency((currency == null || currency.isBlank()) ? "SGD" : currency.trim().toUpperCase(Locale.ROOT));
        entity.setStatus(ReceiptStatus.UPLOADED);
        entity.setBlobName(blobName);

        entity.setContentType(normalizedType);
        entity.setFileName(originalName);
        entity.setFileSizeBytes(file.getSize());

        ReceiptEntity saved = receiptRepository.save(entity);

        log.info("Receipt uploaded id={} user={} file={} size={} contentType={}",
                saved.getId(),
                user.getEmail(),
                saved.getFileName(),
                saved.getFileSizeBytes(),
                saved.getContentType());

        String url = blobStorageService.resolveFileUrl(saved.getBlobName());

        return ReceiptResponse.of(
                saved.getId(),
                url,
                saved.getFileName(),
                saved.getContentType(),
                saved.getFileSizeBytes(),
                saved.getMerchant(),
                saved.getReceiptDate(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                saved.getStatus(),
                saved.getCreatedAt());
    }

    public List<ReceiptResponse> listReceipts(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email"));

        List<ReceiptEntity> list = receiptRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        List<ReceiptResponse> out = new ArrayList<>(list.size());

        for (ReceiptEntity r : list) {
            out.add(ReceiptResponse.of(
                    r.getId(),
                    blobStorageService.resolveFileUrl(r.getBlobName()),
                    r.getFileName(),
                    r.getContentType(),
                    r.getFileSizeBytes(),
                    r.getMerchant(),
                    r.getReceiptDate(),
                    r.getTotalAmount(),
                    r.getCurrency(),
                    r.getStatus(),
                    r.getCreatedAt()));
        }
        return out;
    }

    public ReceiptResponse getReceipt(String userEmail, UUID receiptId) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email"));

        ReceiptEntity r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));

        if (!r.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Forbidden");
        }

        return ReceiptResponse.of(
                r.getId(),
                blobStorageService.resolveFileUrl(r.getBlobName()),
                r.getFileName(),
                r.getContentType(),
                r.getFileSizeBytes(),
                r.getMerchant(),
                r.getReceiptDate(),
                r.getTotalAmount(),
                r.getCurrency(),
                r.getStatus(),
                r.getCreatedAt());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String ct = file.getContentType();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);

        boolean allowedByContentType = (ct != null && ALLOWED_CONTENT_TYPES.contains(ct));
        boolean allowedByExtension = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".pdf");

        if (!allowedByContentType && !allowedByExtension) {
            throw new IllegalArgumentException("Invalid content-type. Allowed: image/jpeg, image/png, application/pdf");
        }

        long size = file.getSize();
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid file");
        }
        if (size > uploadProps.getMaxFileBytes()) {
            throw new IllegalArgumentException("File too large");
        }
    }

    private String safeFileName(String name) {
        String trimmed = name.trim();
        trimmed = trimmed.replace("\\", "_").replace("/", "_");
        if (trimmed.isBlank())
            return "receipt";
        return trimmed;
    }

    private String blankToNull(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String normalizeContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.equalsIgnoreCase("application/octet-stream")) {
            return contentType;
        }
        if (fileName == null) {
            return "application/octet-stream";
        }

        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "image/jpeg";
        if (name.endsWith(".png"))
            return "image/png";
        if (name.endsWith(".pdf"))
            return "application/pdf";

        return "application/octet-stream";
    }

    @Transactional
    public ReceiptEntity createReceipt(UUID userId, MultipartFile file) {

        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setId(UUID.randomUUID());
        receipt.setUserId(userId);
        receipt.setStatus(ReceiptStatus.UPLOADED);
        receipt.setContentType(file.getContentType());
        receipt.setFileName(file.getOriginalFilename());

        String blobName = buildBlobName(userId, receipt.getId(), file.getOriginalFilename());
        receipt.setBlobName(blobName);
        OffsetDateTime now = OffsetDateTime.now();
receipt.setCreatedAt(now);
receipt.setUpdatedAt(now);

        receiptRepository.save(receipt);

        String originalName = safeFileName(
                java.util.Objects.requireNonNullElse(file.getOriginalFilename(), "receipt"));
        String normalizedType = normalizeContentType(file.getContentType(), originalName);

        log.info("[{}] Blob name for receipt upload: {}",
                RequestCorrelation.getRequestId(), blobName);

        // Upload first
        try (InputStream in = file.getInputStream()) {
            blobStorageService.upload(blobName, in, file.getSize(), normalizedType);
        } catch (Exception e) {
            log.error("[{}] Blob upload failed receiptId={} blobName={}",
                    RequestCorrelation.getRequestId(), receipt.getId(), blobName, e);
            throw new IllegalStateException("Failed to upload receipt to storage", e);
        }

        log.info("[{}] BEFORE enqueue receiptId={} status={}",
                RequestCorrelation.getRequestId(), receipt.getId(), receipt.getStatus());

        // Enqueue next. If enqueue fails, delete blob to avoid orphan.
        try {
            // log.info("[{}] BEFORE enqueue receiptId={} status={}",
            // RequestCorrelation.getRequestId(), receipt.getId(), receipt.getStatus());
            queueService.enqueue(receipt.getId(), userId, blobName, normalizedType);
            log.info("[{}] AFTER enqueue receiptId={}", RequestCorrelation.getRequestId(), receipt.getId());
        } catch (Exception e) {
            // Best-effort cleanup (do not hide original error)
            try {
                blobStorageService.deleteIfExists(blobName);
            } catch (Exception cleanupEx) {
                log.warn("[{}] Failed to cleanup blob after enqueue failure blobName={} err={}",
                        RequestCorrelation.getRequestId(), blobName, cleanupEx.getMessage());
            }
            log.error("[{}] OCR enqueue failed receiptId={} userId={} blobName={}",
                    RequestCorrelation.getRequestId(), receipt.getId(), userId, blobName, e);

            throw e;
        }
        log.info("[{}] AFTER enqueue receiptId={}",
                RequestCorrelation.getRequestId(), receipt.getId());
        // Mark OCR_PENDING only after enqueue succeeds
        receipt.setStatus(ReceiptStatus.OCR_PENDING);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receiptRepository.save(receipt);
        log.info("[{}] AFTER status update receiptId={} status={}",
                RequestCorrelation.getRequestId(), receipt.getId(), receipt.getStatus());

        log.info("[{}] Receipt uploaded & OCR enqueued receiptId={} userId={} blobName={}",
                RequestCorrelation.getRequestId(), receipt.getId(), userId, blobName);

        return receipt;
    }

    @SuppressWarnings("unused")
    private static String safeContentType(String ct) {
        if (ct == null || ct.isBlank())
            return "application/octet-stream";
        return ct;
    }

    private String buildBlobName(UUID userId, UUID receiptId, String originalName) {
        String safe = (originalName == null || originalName.isBlank()) ? "receipt"
                : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "receipts/" + userId + "/" + receiptId + "/" + safe;
    }

    public ReceiptResponse getReceiptById(UUID id) {
    ReceiptEntity receipt = receiptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
    return toResponse(receipt);
}

private ReceiptResponse toResponse(ReceiptEntity receipt) {
  String fileUrl = blobStorageService.resolveFileUrl(receipt.getBlobName());

    return ReceiptResponse.of(
            receipt.getId(),
            fileUrl,
            receipt.getFileName(),
            receipt.getContentType(),
            receipt.getFileSizeBytes(),
            receipt.getMerchant(),
            receipt.getReceiptDate(),
            receipt.getTotalAmount(),
            receipt.getCurrency(),
            receipt.getStatus(),
            receipt.getCreatedAt()
    );
}
}
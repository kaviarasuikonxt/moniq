package com.moniq.api.receipt;

import com.moniq.api.receipt.dto.ReceiptResponse;
import com.moniq.api.repository.UserRepository;
import com.moniq.api.auth.UserEntity;
import com.moniq.api.storage.BlobStorageService;
import com.moniq.api.storage.ReceiptUploadProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReceiptService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final BlobStorageService blobStorageService;
    private final ReceiptUploadProperties uploadProps;

    public ReceiptService(
            ReceiptRepository receiptRepository,
            UserRepository userRepository,
            BlobStorageService blobStorageService,
            ReceiptUploadProperties uploadProps
    ) {
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.blobStorageService = blobStorageService;
        this.uploadProps = uploadProps;
    }

    public ReceiptResponse uploadReceipt(
            String userEmail,
            MultipartFile file,
            String merchant,
            OffsetDateTime receiptDate,
            BigDecimal totalAmount,
            String currency
    ) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email"));

        validateFile(file);

        UUID receiptId = UUID.randomUUID();
        String originalName = safeFileName(Objects.requireNonNullElse(file.getOriginalFilename(), "receipt"));
        String blobName = "receipts/" + user.getId() + "/" + receiptId + "/" + originalName;

        try (InputStream in = file.getInputStream()) {
            blobStorageService.upload(blobName, in, file.getSize(), file.getContentType());
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
        entity.setContentType(file.getContentType());
        entity.setFileName(originalName);
        entity.setFileSizeBytes(file.getSize());

        ReceiptEntity saved = receiptRepository.save(entity);

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
                saved.getCreatedAt()
        );
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
                    r.getCreatedAt()
            ));
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
                r.getCreatedAt()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct)) {
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
        // Basic sanitation to avoid weird paths
        trimmed = trimmed.replace("\\", "_").replace("/", "_");
        if (trimmed.isBlank()) return "receipt";
        return trimmed;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
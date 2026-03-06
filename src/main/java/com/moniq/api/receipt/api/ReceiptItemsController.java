// src/main/java/com/moniq/api/receipt/api/ReceiptItemsController.java
package com.moniq.api.receipt.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moniq.api.ocr.OcrService;
import com.moniq.api.ocr.entity.ReceiptItemEntity;
import com.moniq.api.ocr.entity.ReceiptOcrResultEntity;
import com.moniq.api.receipt.ReceiptEntity;
import com.moniq.api.receipt.ReceiptRepository;
import com.moniq.api.receipt.dto.ReceiptItemResponse;
import com.moniq.api.receipt.dto.ReceiptItemsResponse;
import com.moniq.api.receipt.dto.ReceiptOcrResponse;
import com.moniq.api.security.CurrentUserIdResolver;


@RestController
@RequestMapping("/api/receipts")
public class ReceiptItemsController {



    private final ReceiptRepository receiptRepository;
    private final OcrService ocrService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public ReceiptItemsController(ReceiptRepository receiptRepository,
                                  OcrService ocrService,
                                  CurrentUserIdResolver currentUserIdResolver) {
        this.receiptRepository = receiptRepository;
        this.ocrService = ocrService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<?> getReceiptItems(@PathVariable("id") UUID receiptId) {

        ReceiptEntity receipt = receiptRepository.findById(receiptId).orElse(null);
        if (receipt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Receipt not found"));
        }

        UUID currentUserId = currentUserIdResolver.resolveCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("UNAUTHORIZED", "Unauthenticated"));
        }

        if (!currentUserId.equals(receipt.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "Access denied"));
        }

        ReceiptItemsResponse resp = new ReceiptItemsResponse();
        resp.receiptId = receiptId;
        resp.status = String.valueOf(receipt.getStatus());

        // Day 8 rule: if not OCR_COMPLETED, return empty list (HTTP 200)
        if (!"OCR_COMPLETED".equalsIgnoreCase(String.valueOf(receipt.getStatus()))) {
            return ResponseEntity.ok(resp);
        }

        List<ReceiptItemEntity> items = ocrService.getItems(receiptId);
        for (ReceiptItemEntity it : items) {
            ReceiptItemResponse r = new ReceiptItemResponse();
            r.id = it.getId();
            r.lineNo = it.getLineNo();
            r.rawLine = it.getRawLine();
            r.itemName = it.getItemName();
            r.quantity = it.getQuantity();
            r.unitPrice = it.getUnitPrice();
            r.amount = it.getAmount();
            r.currency = it.getCurrency();
            r.category = it.getCategory();
            r.confidence = it.getConfidence();
            resp.items.add(r);
        }

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/ocr")
    public ResponseEntity<?> getReceiptOcr(@PathVariable("id") UUID receiptId) {

        ReceiptEntity receipt = receiptRepository.findById(receiptId).orElse(null);
        if (receipt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Receipt not found"));
        }

        UUID currentUserId = currentUserIdResolver.resolveCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("UNAUTHORIZED", "Unauthenticated"));
        }

        if (!currentUserId.equals(receipt.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "Access denied"));
        }

        ReceiptOcrResultEntity ocr = ocrService.getOcrResult(receiptId).orElse(null);
        if (ocr == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "OCR result not found"));
        }

        ReceiptOcrResponse resp = new ReceiptOcrResponse();
        resp.receiptId = receiptId;
        resp.provider = ocr.getProvider();
        resp.rawText = ocr.getRawText();
        resp.createdAt = ocr.getCreatedAt();

        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }



/*
    private final ReceiptRepository receiptRepository;
    private final OcrService ocrService;

    private final UserRepository userRepository;

public ReceiptItemsController(ReceiptRepository receiptRepository, OcrService ocrService, UserRepository userRepository) {
    this.receiptRepository = receiptRepository;
    this.ocrService = ocrService;
    this.userRepository = userRepository;
}
   /*  public ReceiptItemsController(ReceiptRepository receiptRepository, OcrService ocrService) {
        this.receiptRepository = receiptRepository;
        this.ocrService = ocrService;
    }*/
/*
    @GetMapping("/{id}/items")
    public ResponseEntity<?> getReceiptItems(@PathVariable("id") UUID receiptId) {

        ReceiptEntity receipt = receiptRepository.findById(receiptId).orElse(null);
        if (receipt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Receipt not found"));
        }

        UUID currentUserId = currentUserIdOrThrow();
        if (!currentUserId.equals(receipt.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "Access denied"));
        }

        ReceiptItemsResponse resp = new ReceiptItemsResponse();
        resp.receiptId = receiptId;
        resp.status = String.valueOf(receipt.getStatus());

        // Consistent rule for Day 8: if not completed, return empty list (HTTP 200)
        if (!"OCR_COMPLETED".equalsIgnoreCase(String.valueOf(receipt.getStatus()))) {
            return ResponseEntity.ok(resp);
        }

        List<ReceiptItemEntity> items = ocrService.getItems(receiptId);
        for (ReceiptItemEntity it : items) {
            ReceiptItemResponse r = new ReceiptItemResponse();
            r.id = it.getId();
            r.lineNo = it.getLineNo();
            r.rawLine = it.getRawLine();
            r.itemName = it.getItemName();
            r.quantity = it.getQuantity();
            r.unitPrice = it.getUnitPrice();
            r.amount = it.getAmount();
            r.currency = it.getCurrency();
            r.category = it.getCategory();
            r.confidence = it.getConfidence();
            resp.items.add(r);
        }

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/ocr")
    public ResponseEntity<?> getReceiptOcr(@PathVariable("id") UUID receiptId) {

        ReceiptEntity receipt = receiptRepository.findById(receiptId).orElse(null);
        if (receipt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Receipt not found"));
        }

        UUID currentUserId = currentUserIdOrThrow();
        if (!currentUserId.equals(receipt.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "Access denied"));
        }

        ReceiptOcrResultEntity ocr = ocrService.getOcrResult(receiptId).orElse(null);
        if (ocr == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "OCR result not found"));
        }

        ReceiptOcrResponse resp = new ReceiptOcrResponse();
        resp.receiptId = receiptId;
        resp.provider = ocr.getProvider();
        resp.rawText = ocr.getRawText();
        resp.createdAt = ocr.getCreatedAt();

        return ResponseEntity.ok(resp);
    }

    /**
     * Minimal error body (no dependency on ApiErrorResponse).
     * If your GlobalAuthExceptionHandler expects a specific format,
     * we can replace this later with your real DTO once you share it.
     */
    /* 
    private Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }*/

    /**
     * IMPORTANT: This must match your existing JWT authentication principal.
     * This method tries common patterns:
     * - auth.getName() == userId UUID
     * - principal Map contains "userId" or "sub"
     */

/* 

    private UUID currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Unauthenticated");
        }

        // 1) If auth name is UUID (your custom token might do this)
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception ignored) {
        }

        // 2) If using Spring Resource Server JWT
        if (auth instanceof JwtAuthenticationToken jat) {
            Jwt jwt = jat.getToken();

            // Try common claim keys
            String userId = firstNonBlank(
                    jwt.getClaimAsString("userId"),
                    jwt.getClaimAsString("uid"),
                    jwt.getSubject() // sometimes you store UUID in sub
            );

            if (userId != null) {
                try {
                    return UUID.fromString(userId);
                } catch (Exception ignored) {
                }
            }
        }

        // 3) If principal itself is Jwt
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userId = firstNonBlank(
                    jwt.getClaimAsString("userId"),
                    jwt.getClaimAsString("uid"),
                    jwt.getSubject());

            if (userId != null) {
                try {
                    return UUID.fromString(userId);
                } catch (Exception ignored) {
                }
            }
        }

        // 4) Fallback: Map principal
        if (principal instanceof Map<?, ?> map) {
            Object userId = map.get("userId");
            if (userId != null) {
                try {
                    return UUID.fromString(String.valueOf(userId));
                } catch (Exception ignored) {
                }
            }
            Object sub = map.get("sub");
            if (sub != null) {
                try {
                    return UUID.fromString(String.valueOf(sub));
                } catch (Exception ignored) {
                }
            }
        }

        throw new IllegalStateException("Cannot resolve userId from authentication");
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null)
            return null;
        for (String v : vals) {
            if (v != null && !v.isBlank())
                return v;
        }
        return null;
    }
    */




}
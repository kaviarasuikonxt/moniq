// src/main/java/com/moniq/api/receipt/api/ReceiptController.java
package com.moniq.api.receipt.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moniq.api.receipt.ReceiptEntity;
import com.moniq.api.receipt.ReceiptService;
import com.moniq.api.receipt.dto.ReceiptResponse;
import com.moniq.api.security.CurrentUserIdResolver;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public ReceiptController(ReceiptService receiptService,
                             CurrentUserIdResolver currentUserIdResolver) {
        this.receiptService = receiptService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "merchant", required = false) String merchant,
            @RequestParam(value = "receiptDate", required = false) String receiptDate,
            @RequestParam(value = "totalAmount", required = false) BigDecimal totalAmount,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        UUID userId = currentUserIdResolver.resolveCurrentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("UNAUTHORIZED", "Unauthenticated"));
        }

        ReceiptEntity receipt = receiptService.createReceipt(userId, file);
        ReceiptResponse response = receiptService.getReceiptById(receipt.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<ReceiptResponse> list(Authentication authentication) {
        return receiptService.listReceipts(authentication.getName());
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(Authentication authentication, @PathVariable("id") UUID id) {
        return receiptService.getReceipt(authentication.getName(), id);
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
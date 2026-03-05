package com.moniq.api.receipt.api;

import com.moniq.api.receipt.ReceiptService;
import com.moniq.api.receipt.dto.ReceiptResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptResponse upload(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "merchant", required = false) String merchant,
            @RequestParam(value = "receiptDate", required = false) String receiptDate,
            @RequestParam(value = "totalAmount", required = false) BigDecimal totalAmount,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        String email = authentication.getName();

        OffsetDateTime parsedDate = null;
        if (receiptDate != null && !receiptDate.isBlank()) {
            parsedDate = OffsetDateTime.parse(receiptDate.trim());
        }

        return receiptService.uploadReceipt(email, file, merchant, parsedDate, totalAmount, currency);
    }

    @GetMapping
    public List<ReceiptResponse> list(Authentication authentication) {
        return receiptService.listReceipts(authentication.getName());
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(Authentication authentication, @PathVariable("id") UUID id) {
        return receiptService.getReceipt(authentication.getName(), id);
    }
}
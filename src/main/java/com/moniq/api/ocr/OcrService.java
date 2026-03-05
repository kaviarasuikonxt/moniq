// src/main/java/com/moniq/api/ocr/OcrService.java
package com.moniq.api.ocr;

import com.moniq.api.categorization.AiCategorizer;
import com.moniq.api.categorization.CategorizationResult;
import com.moniq.api.ocr.entity.ReceiptItemEntity;
import com.moniq.api.ocr.entity.ReceiptOcrResultEntity;
import com.moniq.api.ocr.repository.ReceiptItemRepository;
import com.moniq.api.ocr.repository.ReceiptOcrResultRepository;
import com.moniq.api.web.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    // Price at end of line: ... 12.34
    private static final Pattern PRICE_AT_END = Pattern.compile("(\\d+\\.\\d{2})\\s*$");

    private final OcrProvider ocrProvider;
    private final ReceiptOcrResultRepository ocrRepo;
    private final ReceiptItemRepository itemRepo;
    private final AiCategorizer categorizer;

    @Value("${app.ai.enabled:false}")
    private final boolean aiEnabled;

    public OcrService(
            OcrProvider ocrProvider,
            ReceiptOcrResultRepository ocrRepo,
            ReceiptItemRepository itemRepo,
            AiCategorizer categorizer,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.ocrProvider = ocrProvider;
        this.ocrRepo = ocrRepo;
        this.itemRepo = itemRepo;
        this.categorizer = categorizer;
        this.aiEnabled = aiEnabled;
    }

    public OcrProviderResult runOcrAndPersist(UUID receiptId, InputStream blobStream, String contentType) {
        OcrProviderResult result = ocrProvider.read(blobStream, contentType);

        ReceiptOcrResultEntity entity = new ReceiptOcrResultEntity();
        entity.setReceiptId(receiptId);
        entity.setRawText(result.getRawText() == null ? "" : result.getRawText());
        entity.setOcrJson(result.getNormalizedJson());
        entity.setProvider(ocrProvider.providerName());
        entity.setCreatedAt(OffsetDateTime.now());
        ocrRepo.save(entity);

        log.info("[{}] OCR persisted receiptId={} chars={}",
            RequestCorrelation.getRequestId(), receiptId, entity.getRawText().length());

        // Rebuild items idempotently
        itemRepo.deleteByReceiptId(receiptId);

        List<ReceiptItemEntity> items = extractItems(receiptId, entity.getRawText());
        items = categorize(items);

        itemRepo.saveAll(items);

        log.info("[{}] Items extracted receiptId={} items={}",
            RequestCorrelation.getRequestId(), receiptId, items.size());

        return result;
    }

    public Optional<ReceiptOcrResultEntity> getOcrResult(UUID receiptId) {
        return ocrRepo.findById(receiptId);
    }

    public List<ReceiptItemEntity> getItems(UUID receiptId) {
        return itemRepo.findByReceiptIdOrderByLineNoAsc(receiptId);
    }

    private List<ReceiptItemEntity> extractItems(UUID receiptId, String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();

        String[] lines = rawText.split("\\r?\\n");
        List<ReceiptItemEntity> out = new ArrayList<>();
        int lineNo = 1;

        for (String line : lines) {
            if (line == null) continue;
            String rawLine = line.trim();
            if (rawLine.isBlank()) continue;

            Matcher m = PRICE_AT_END.matcher(rawLine);
            if (!m.find()) continue;

            String amountStr = m.group(1);
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountStr);
            } catch (Exception ignored) {
                continue;
            }

            String namePart = rawLine.substring(0, m.start(1)).trim();
            if (namePart.isBlank()) namePart = null;

            ReceiptItemEntity item = new ReceiptItemEntity();
            item.setId(UUID.randomUUID());
            item.setReceiptId(receiptId);
            item.setLineNo(lineNo++);
            item.setRawLine(rawLine);
            item.setItemName(namePart);
            item.setAmount(amount);
            item.setCurrency("SGD");
            item.setCreatedAt(OffsetDateTime.now());

            out.add(item);
        }
        return out;
    }

    private List<ReceiptItemEntity> categorize(List<ReceiptItemEntity> items) {
        if (items.isEmpty()) return items;

        for (ReceiptItemEntity item : items) {
            CategorizationResult r = categorizer.categorize(item.getItemName(), item.getRawLine());
            item.setCategory(r.getCategory());
            item.setConfidence(BigDecimal.valueOf(r.getConfidence()).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return items;
    }
}
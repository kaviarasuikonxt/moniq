// src/main/java/com/moniq/api/ocr/OcrService.java
package com.moniq.api.ocr;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.moniq.api.categorization.AiCategorizer;
import com.moniq.api.categorization.CategorizationResult;
import com.moniq.api.ocr.entity.ReceiptItemEntity;
import com.moniq.api.ocr.entity.ReceiptOcrResultEntity;
import com.moniq.api.ocr.repository.ReceiptItemRepository;
import com.moniq.api.ocr.repository.ReceiptOcrResultRepository;
import com.moniq.api.parsing.ReceiptItemParser;
import com.moniq.api.parsing.ReceiptLineFilterService;
import com.moniq.api.parsing.ReceiptLineNormalizer;
import com.moniq.api.web.RequestCorrelation;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrProvider ocrProvider;
    private final ReceiptOcrResultRepository ocrRepo;
    private final ReceiptItemRepository itemRepo;
    private final AiCategorizer categorizer;

    private final ReceiptItemParser itemParser;

    @SuppressWarnings("unused")
    private final boolean aiEnabled;

    public OcrService(
            OcrProvider ocrProvider,
            ReceiptOcrResultRepository ocrRepo,
            ReceiptItemRepository itemRepo,
            AiCategorizer categorizer,
            ReceiptLineFilterService lineFilter,
            ReceiptLineNormalizer lineNormalizer,
            ReceiptItemParser itemParser,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.ocrProvider = ocrProvider;
        this.ocrRepo = ocrRepo;
        this.itemRepo = itemRepo;
        this.categorizer = categorizer;
        this.itemParser = itemParser;
        this.aiEnabled = aiEnabled;
    }

    public OcrProviderResult runOcrAndPersist(UUID receiptId, InputStream blobStream, String contentType) {

        OcrProviderResult result = ocrProvider.read(blobStream, contentType);

        ReceiptOcrResultEntity entity = new ReceiptOcrResultEntity();
        entity.setReceiptId(receiptId);
        entity.setRawText(result.getRawText() == null ? "" : result.getRawText());

        String normalizedJson = result.getNormalizedJson();
        if (normalizedJson == null || normalizedJson.isBlank()) {
            normalizedJson = "{}";
        }

        entity.setOcrJson(normalizedJson);
        entity.setProvider(ocrProvider.providerName());
        entity.setCreatedAt(OffsetDateTime.now());

        ocrRepo.save(entity);

        log.info("[{}] OCR persisted receiptId={} chars={}",
                RequestCorrelation.getRequestId(), receiptId, entity.getRawText().length());

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

    if (rawText == null || rawText.isBlank()) {
        return List.of();
    }

    log.info("[{}] OCR parsing receiptId={} textLength={}",
            RequestCorrelation.getRequestId(), receiptId, rawText.length());

    List<ReceiptItemParser.ParsedItem> parsedItems =
            itemParser.parseReceipt(rawText);

    List<ReceiptItemEntity> items = new ArrayList<>();

    int lineNo = 1;

    for (ReceiptItemParser.ParsedItem parsed : parsedItems) {

        if (parsed.getAmount() == null ||
            parsed.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            continue;
        }

        ReceiptItemEntity item = new ReceiptItemEntity();

        item.setId(UUID.randomUUID());
        item.setReceiptId(receiptId);
        item.setLineNo(lineNo++);

        item.setRawLine(parsed.getRawLine());
        item.setItemName(parsed.getItemName());

        item.setQuantity(parsed.getQuantity());
        item.setAmount(parsed.getAmount());
        item.setCurrency("SGD");

        item.setCreatedAt(OffsetDateTime.now());

        items.add(item);
    }

    log.info("[{}] Parsed items receiptId={} items={}",
            RequestCorrelation.getRequestId(), receiptId, items.size());

    return items;
}

    private List<ReceiptItemEntity> categorize(List<ReceiptItemEntity> items) {

        if (items.isEmpty()) {
            return items;
        }

        for (ReceiptItemEntity item : items) {

            CategorizationResult r = categorizer.categorize(
                    item.getItemName(),
                    item.getRawLine()
            );

            item.setCategory(r.getCategory());
            item.setConfidence(
                    r.getConfidence()
                            .setScale(2, java.math.RoundingMode.HALF_UP)
            );
        }
      
        
        return items;
    }
}
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
import com.moniq.api.ocr.layout.AzureOcrLayoutParser;
import com.moniq.api.ocr.layout.OcrLayoutDocument;
import com.moniq.api.ocr.layout.OcrLineRowGrouper;
import com.moniq.api.ocr.layout.OcrRow;
import com.moniq.api.ocr.layout.OcrRowTextComposer;
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

    private final AzureOcrLayoutParser azureOcrLayoutParser;
    private final OcrLineRowGrouper rowGrouper;
    private final OcrRowTextComposer rowComposer;

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
            AzureOcrLayoutParser azureOcrLayoutParser,
            OcrLineRowGrouper rowGrouper,
            OcrRowTextComposer rowComposer,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.ocrProvider = ocrProvider;
        this.ocrRepo = ocrRepo;
        this.itemRepo = itemRepo;
        this.categorizer = categorizer;
        this.itemParser = itemParser;
        this.azureOcrLayoutParser = azureOcrLayoutParser;
        this.rowGrouper = rowGrouper;
        this.rowComposer = rowComposer;
        this.aiEnabled = aiEnabled;
    }

    public OcrProviderResult runOcrAndPersist(UUID receiptId, InputStream blobStream, String contentType) {

        String requestId = RequestCorrelation.getRequestId();

        log.info("[{}] OCR processing started receiptId={} provider={}",
                requestId, receiptId, ocrProvider.providerName());

        OcrProviderResult result = ocrProvider.read(blobStream, contentType);

        ReceiptOcrResultEntity entity = ocrRepo.findById(receiptId)
                .orElseGet(ReceiptOcrResultEntity::new);

        entity.setReceiptId(receiptId);
        entity.setRawText(result.getRawText());
        entity.setNormalizedJson(result.getNormalizedJson());
        entity.setProvider(ocrProvider.providerName());
        entity.setCreatedAt(OffsetDateTime.now());

        ocrRepo.save(entity);

        log.info("[{}] OCR persisted receiptId={} rawTextLength={} jsonLength={}",
                requestId,
                receiptId,
                entity.getRawText().length(),
                entity.getNormalizedJson().length());

        itemRepo.deleteByReceiptId(receiptId);

        List<ReceiptItemEntity> rawItems = extractItems(receiptId, entity.getRawText());

        String layoutText = buildLayoutText(entity.getNormalizedJson());

        List<ReceiptItemEntity> layoutItems = extractItems(receiptId, layoutText);

        List<ReceiptItemEntity> selected = selectBetterResult(rawItems, layoutItems);

        selected = categorize(selected);

        itemRepo.saveAll(selected);

        log.info("[{}] Final OCR items selected receiptId={} items={}",
                requestId, receiptId, selected.size());

        return result;
    }

    private String buildLayoutText(String normalizedJson) {

        String requestId = RequestCorrelation.getRequestId();

        if (normalizedJson == null || normalizedJson.isBlank() || "{}".equals(normalizedJson.trim())) {
            return "";
        }

        try {

            OcrLayoutDocument document = azureOcrLayoutParser.parse(normalizedJson);

            if (document == null || !document.hasPages()) {
                return "";
            }

            List<OcrRow> rows = rowGrouper.groupDocument(document);

            if (rows == null || rows.isEmpty()) {
                return "";
            }

            String text = rowComposer.composeDocumentText(rows);

            log.info("[{}] Layout reconstruction rows={} length={}",
                    requestId, rows.size(), text.length());

            return text;

        } catch (Exception ex) {

            log.warn("[{}] Layout parsing failed fallback rawText", requestId, ex);

            return "";
        }
    }

    private List<ReceiptItemEntity> extractItems(UUID receiptId, String text) {

        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ReceiptItemParser.ParsedItem> parsed = itemParser.parseReceipt(text);

        List<ReceiptItemEntity> items = new ArrayList<>();

        int lineNo = 1;

        for (ReceiptItemParser.ParsedItem p : parsed) {

            if (p.getAmount() == null ||
                    p.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                continue;
            }

            ReceiptItemEntity item = new ReceiptItemEntity();

            item.setId(UUID.randomUUID());
            item.setReceiptId(receiptId);
            item.setLineNo(lineNo++);
            item.setRawLine(p.getRawLine());
            item.setItemName(p.getItemName());
            item.setQuantity(p.getQuantity());
            item.setUnitPrice(p.getUnitPrice());
            item.setAmount(p.getAmount());
            item.setCurrency("SGD");
            item.setCreatedAt(OffsetDateTime.now());

            items.add(item);
        }

        return items;
    }

    private List<ReceiptItemEntity> selectBetterResult(
            List<ReceiptItemEntity> raw,
            List<ReceiptItemEntity> layout) {

        String requestId = RequestCorrelation.getRequestId();

        int rawScore = score(raw);
        int layoutScore = score(layout);

        log.info("[{}] OCR scoring rawScore={} layoutScore={}",
                requestId, rawScore, layoutScore);

        if (layoutScore > rawScore) {
            log.info("[{}] Layout parsing selected", requestId);
            return layout;
        }

        log.info("[{}] Raw text parsing selected", requestId);
        return raw;
    }

    private int score(List<ReceiptItemEntity> items) {

        if (items == null) return 0;

        int score = 0;

        for (ReceiptItemEntity i : items) {

            if (i.getAmount() != null &&
                    i.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                score += 3;
            }

            if (i.getItemName() != null &&
                    i.getItemName().length() > 2) {
                score += 1;
            }

            if (i.getQuantity() != null) {
                score += 1;
            }
        }

        return score;
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

    public Optional<ReceiptOcrResultEntity> getOcrResult(UUID receiptId) {
        return ocrRepo.findById(receiptId);
    }

    public List<ReceiptItemEntity> getItems(UUID receiptId) {
        return itemRepo.findByReceiptIdOrderByLineNoAsc(receiptId);
    }
}
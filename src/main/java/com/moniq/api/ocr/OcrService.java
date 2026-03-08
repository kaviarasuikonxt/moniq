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
    private final OcrLineRowGrouper ocrLineRowGrouper;
    private final OcrRowTextComposer ocrRowTextComposer;

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
            OcrLineRowGrouper ocrLineRowGrouper,
            OcrRowTextComposer ocrRowTextComposer,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.ocrProvider = ocrProvider;
        this.ocrRepo = ocrRepo;
        this.itemRepo = itemRepo;
        this.categorizer = categorizer;
        this.itemParser = itemParser;
        this.azureOcrLayoutParser = azureOcrLayoutParser;
        this.ocrLineRowGrouper = ocrLineRowGrouper;
        this.ocrRowTextComposer = ocrRowTextComposer;
        this.aiEnabled = aiEnabled;
    }

    public OcrProviderResult runOcrAndPersist(UUID receiptId, InputStream blobStream, String contentType) {
        String requestId = RequestCorrelation.getRequestId();

        log.info("[{}] OCR processing started receiptId={} provider={} contentType={}",
                requestId, receiptId, ocrProvider.providerName(), safeContentType(contentType));

        OcrProviderResult result = ocrProvider.read(blobStream, contentType);

        ReceiptOcrResultEntity entity = ocrRepo.findById(receiptId)
                .orElseGet(ReceiptOcrResultEntity::new);

        entity.setReceiptId(receiptId);
        entity.setRawText(result.getRawText());

        String normalizedJson = result.getNormalizedJson();
        if (normalizedJson == null || normalizedJson.isBlank()) {
            log.warn("[{}] OCR normalizedJson missing receiptId={} provider={}",
                    requestId, receiptId, ocrProvider.providerName());
            normalizedJson = "{}";
        }

        entity.setNormalizedJson(normalizedJson);
        entity.setProvider(ocrProvider.providerName());
        entity.setCreatedAt(OffsetDateTime.now());

        ocrRepo.save(entity);

        log.info("[{}] OCR persisted receiptId={} provider={} rawTextLength={} normalizedJsonLength={}",
                requestId,
                receiptId,
                entity.getProvider(),
                entity.getRawText() == null ? 0 : entity.getRawText().length(),
                entity.getNormalizedJson() == null ? 0 : entity.getNormalizedJson().length());

        itemRepo.deleteByReceiptId(receiptId);

        String parserInputText = buildParserInputText(receiptId, entity);

        List<ReceiptItemEntity> items = extractItems(receiptId, parserInputText);
        items = categorize(items);
        itemRepo.saveAll(items);

        log.info("[{}] Items extracted receiptId={} items={}",
                requestId, receiptId, items.size());

        return result;
    }

    public Optional<ReceiptOcrResultEntity> getOcrResult(UUID receiptId) {
        return ocrRepo.findById(receiptId);
    }

    public List<ReceiptItemEntity> getItems(UUID receiptId) {
        return itemRepo.findByReceiptIdOrderByLineNoAsc(receiptId);
    }

    private String buildParserInputText(UUID receiptId, ReceiptOcrResultEntity entity) {
        String requestId = RequestCorrelation.getRequestId();
        String normalizedJson = entity.getNormalizedJson();

        if (normalizedJson == null || normalizedJson.isBlank() || "{}".equals(normalizedJson.trim())) {
            log.info("[{}] OCR parser input fallback receiptId={} reason=no_normalized_json",
                    requestId, receiptId);
            return entity.getRawText();
        }

        try {
            OcrLayoutDocument document = azureOcrLayoutParser.parse(normalizedJson);
            if (document == null || !document.hasPages()) {
                log.warn("[{}] OCR layout parse returned empty document receiptId={} fallback=raw_text",
                        requestId, receiptId);
                return entity.getRawText();
            }

            List<OcrRow> rows = ocrLineRowGrouper.groupDocument(document);
            if (rows == null || rows.isEmpty()) {
                log.warn("[{}] OCR row grouping returned empty rows receiptId={} fallback=raw_text",
                        requestId, receiptId);
                return entity.getRawText();
            }

            String layoutText = ocrRowTextComposer.composeDocumentText(rows);
            if (layoutText == null || layoutText.isBlank()) {
                log.warn("[{}] OCR layout text compose returned blank receiptId={} fallback=raw_text",
                        requestId, receiptId);
                return entity.getRawText();
            }

            log.info("[{}] OCR layout-first parser input selected receiptId={} rows={} layoutTextLength={} rawTextLength={}",
                    requestId,
                    receiptId,
                    rows.size(),
                    layoutText.length(),
                    entity.getRawText() == null ? 0 : entity.getRawText().length());

            return layoutText;
        } catch (Exception ex) {
            log.error("[{}] OCR layout-first parser input failed receiptId={} fallback=raw_text",
                    requestId, receiptId, ex);
            return entity.getRawText();
        }
    }

    private List<ReceiptItemEntity> extractItems(UUID receiptId, String parserInputText) {
        if (parserInputText == null || parserInputText.isBlank()) {
            log.warn("[{}] OCR parsing skipped receiptId={} reason=empty_parser_input",
                    RequestCorrelation.getRequestId(), receiptId);
            return List.of();
        }

        log.info("[{}] OCR parsing started receiptId={} parserInputLength={}",
                RequestCorrelation.getRequestId(), receiptId, parserInputText.length());

        List<ReceiptItemParser.ParsedItem> parsedItems = itemParser.parseReceipt(parserInputText);

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
            item.setUnitPrice(parsed.getUnitPrice());
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
                    r.getConfidence().setScale(2, java.math.RoundingMode.HALF_UP)
            );
        }

        return items;
    }

    private String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "unknown";
        }
        return contentType.trim();
    }
}
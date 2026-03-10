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
import com.moniq.api.parsing.format.ReceiptFormat;
import com.moniq.api.parsing.format.ReceiptFormatDetector;
import com.moniq.api.parsing.format.ReceiptParsingInput;
import com.moniq.api.parsing.format.ReceiptParsingStrategy;
import com.moniq.api.parsing.format.ReceiptParsingStrategyResolver;
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
    private final ReceiptFormatDetector receiptFormatDetector;
    private final ReceiptParsingStrategyResolver receiptParsingStrategyResolver;

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
            ReceiptFormatDetector receiptFormatDetector,
            ReceiptParsingStrategyResolver receiptParsingStrategyResolver,
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
        this.receiptFormatDetector = receiptFormatDetector;
        this.receiptParsingStrategyResolver = receiptParsingStrategyResolver;
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

        String layoutText = buildLayoutText(entity.getNormalizedJson());

        ReceiptFormat detectedFormat = receiptFormatDetector.detect(entity.getRawText());

        log.info("[{}] Receipt format detected receiptId={} format={}",
                requestId,
                receiptId,
                detectedFormat);

        ReceiptParsingInput parsingInput = new ReceiptParsingInput(
                entity.getRawText(),
                layoutText,
                entity.getNormalizedJson()
        );

        ReceiptParsingStrategy strategy = receiptParsingStrategyResolver.resolve(detectedFormat);

        log.info("[{}] Receipt parsing strategy selected receiptId={} format={} strategy={}",
                requestId,
                receiptId,
                detectedFormat,
                strategy.getClass().getSimpleName());

        String parserInputText = strategy.buildParserInput(parsingInput);

        if (parserInputText == null || parserInputText.isBlank()) {
            log.warn("[{}] Strategy returned empty parser input receiptId={} format={} fallback=raw_text",
                    requestId,
                    receiptId,
                    detectedFormat);
            parserInputText = entity.getRawText();
        }

        log.info("[{}] Final parser input ready receiptId={} format={} parserInputLength={} rawTextLength={} layoutTextLength={}",
                requestId,
                receiptId,
                detectedFormat,
                parserInputText == null ? 0 : parserInputText.length(),
                entity.getRawText() == null ? 0 : entity.getRawText().length(),
                layoutText == null ? 0 : layoutText.length());

        List<ReceiptItemEntity> items = extractItemsFromText(receiptId, parserInputText, detectedFormat.name());
        items = categorize(items);
        itemRepo.saveAll(items);

        log.info("[{}] Final OCR items saved receiptId={} format={} items={}",
                requestId,
                receiptId,
                detectedFormat,
                items.size());

        return result;
    }

    public Optional<ReceiptOcrResultEntity> getOcrResult(UUID receiptId) {
        return ocrRepo.findById(receiptId);
    }

    public List<ReceiptItemEntity> getItems(UUID receiptId) {
        return itemRepo.findByReceiptIdOrderByLineNoAsc(receiptId);
    }

    private String buildLayoutText(String normalizedJson) {
        String requestId = RequestCorrelation.getRequestId();

        if (normalizedJson == null || normalizedJson.isBlank() || "{}".equals(normalizedJson.trim())) {
            log.info("[{}] OCR layout reconstruction skipped reason=no_normalized_json", requestId);
            return "";
        }

        try {
            OcrLayoutDocument document = azureOcrLayoutParser.parse(normalizedJson);
            if (document == null || !document.hasPages()) {
                log.warn("[{}] OCR layout parse returned empty document fallback=raw_text",
                        requestId);
                return "";
            }

            List<OcrRow> rows = rowGrouper.groupDocument(document);
            if (rows == null || rows.isEmpty()) {
                log.warn("[{}] OCR row grouping returned empty rows fallback=raw_text",
                        requestId);
                return "";
            }

            String layoutText = rowComposer.composeDocumentText(rows);
            if (layoutText == null || layoutText.isBlank()) {
                log.warn("[{}] OCR layout compose returned blank text fallback=raw_text",
                        requestId);
                return "";
            }

            log.info("[{}] OCR layout reconstruction completed rows={} layoutTextLength={}",
                    requestId,
                    rows.size(),
                    layoutText.length());

            return layoutText;
        } catch (Exception ex) {
            log.error("[{}] OCR layout reconstruction failed fallback=raw_text",
                    requestId, ex);
            return "";
        }
    }

    private List<ReceiptItemEntity> extractItemsFromText(UUID receiptId, String text, String sourceLabel) {
        if (text == null || text.isBlank()) {
            log.warn("[{}] OCR parsing skipped receiptId={} source={} reason=empty_text",
                    RequestCorrelation.getRequestId(), receiptId, sourceLabel);
            return List.of();
        }

        log.info("[{}] OCR parsing started receiptId={} source={} textLength={}",
                RequestCorrelation.getRequestId(), receiptId, sourceLabel, text.length());

        List<ReceiptItemParser.ParsedItem> parsedItems = itemParser.parseReceipt(text);

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

        log.info("[{}] OCR parsing completed receiptId={} source={} items={}",
                RequestCorrelation.getRequestId(), receiptId, sourceLabel, items.size());

        return items;
    }

  private List<ReceiptItemEntity> categorize(List<ReceiptItemEntity> items) {
    if (items.isEmpty()) {
        log.warn("[{}] Categorization skipped reason=no_items",
                RequestCorrelation.getRequestId());
        return items;
    }

    for (ReceiptItemEntity item : items) {
        CategorizationResult r = categorizer.categorize(
                item.getItemName(),
                item.getRawLine()
        );

        item.setCategory(r.getCategory());
        item.setSubcategory(r.getSubcategory());
        item.setConfidence(
                r.getConfidence().setScale(2, java.math.RoundingMode.HALF_UP)
        );
        item.setCategorySource(r.getSource());
    }

    log.info("[{}] Categorization completed items={}",
            RequestCorrelation.getRequestId(),
            items.size());

    return items;
}

    private String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "unknown";
        }
        return contentType.trim();
    }
}
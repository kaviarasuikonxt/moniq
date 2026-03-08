package com.moniq.api.ocr;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{10,14}$");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[-$]?\\d+(\\.\\d{1,3})?$");
    private static final Pattern HEADER_DECORATOR_PATTERN = Pattern.compile("^[\\-*_=#]{2,}$");

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

        String normalizedJson = result.getNormalizedJson();
        if (normalizedJson == null || normalizedJson.isBlank()) {
            normalizedJson = "{}";
        }

        entity.setNormalizedJson(normalizedJson);
        entity.setProvider(ocrProvider.providerName());
        entity.setCreatedAt(OffsetDateTime.now());

        ocrRepo.save(entity);

        log.info("[{}] OCR persisted receiptId={} rawTextLength={} jsonLength={}",
                requestId,
                receiptId,
                entity.getRawText() == null ? 0 : entity.getRawText().length(),
                entity.getNormalizedJson() == null ? 0 : entity.getNormalizedJson().length());

        itemRepo.deleteByReceiptId(receiptId);

        ReceiptStyle style = detectReceiptStyle(entity.getRawText());
        String layoutText = buildLayoutText(entity.getNormalizedJson());

        log.info("[{}] OCR style detected receiptId={} style={}",
                requestId, receiptId, style);

        List<ReceiptItemEntity> rawItems = extractItemsFromText(receiptId, entity.getRawText(), "RAW");
        List<ReceiptItemEntity> layoutItems = extractItemsFromText(receiptId, layoutText, "LAYOUT");

        String filteredRaw = maybeFilterByStyle(entity.getRawText(), style);
        String filteredLayout = maybeFilterByStyle(layoutText, style);

        List<ReceiptItemEntity> filteredRawItems = extractItemsFromText(receiptId, filteredRaw, "FILTERED_RAW");
        List<ReceiptItemEntity> filteredLayoutItems = extractItemsFromText(receiptId, filteredLayout, "FILTERED_LAYOUT");

        List<ReceiptItemEntity> selected = selectBestCandidate(
                receiptId,
                style,
                rawItems,
                layoutItems,
                filteredRawItems,
                filteredLayoutItems
        );

        selected = categorize(selected);
        itemRepo.saveAll(selected);

        log.info("[{}] Final OCR items selected receiptId={} items={}",
                requestId, receiptId, selected.size());

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
            log.warn("[{}] Layout reconstruction skipped reason=no_normalized_json", requestId);
            return "";
        }

        try {
            OcrLayoutDocument document = azureOcrLayoutParser.parse(normalizedJson);

            if (document == null || !document.hasPages()) {
                log.warn("[{}] Layout reconstruction skipped reason=no_pages", requestId);
                return "";
            }

            List<OcrRow> rows = rowGrouper.groupDocument(document);
            if (rows == null || rows.isEmpty()) {
                log.warn("[{}] Layout reconstruction skipped reason=no_rows", requestId);
                return "";
            }

            String text = rowComposer.composeDocumentText(rows);

            log.info("[{}] Layout reconstruction completed rows={} textLength={}",
                    requestId, rows.size(), text == null ? 0 : text.length());

            return text == null ? "" : text.trim();

        } catch (Exception ex) {
            log.error("[{}] Layout reconstruction failed", requestId, ex);
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

    private List<ReceiptItemEntity> selectBestCandidate(
            UUID receiptId,
            ReceiptStyle style,
            List<ReceiptItemEntity> rawItems,
            List<ReceiptItemEntity> layoutItems,
            List<ReceiptItemEntity> filteredRawItems,
            List<ReceiptItemEntity> filteredLayoutItems
    ) {
        String requestId = RequestCorrelation.getRequestId();

        ScoredCandidate rawCandidate = new ScoredCandidate("RAW", rawItems, score(rawItems));
        ScoredCandidate layoutCandidate = new ScoredCandidate("LAYOUT", layoutItems, score(layoutItems));
        ScoredCandidate filteredRawCandidate = new ScoredCandidate("FILTERED_RAW", filteredRawItems, score(filteredRawItems));
        ScoredCandidate filteredLayoutCandidate = new ScoredCandidate("FILTERED_LAYOUT", filteredLayoutItems, score(filteredLayoutItems));

        applyStyleBias(style, rawCandidate, layoutCandidate, filteredRawCandidate, filteredLayoutCandidate);

        ScoredCandidate best = rawCandidate;
        best = better(best, layoutCandidate);
        best = better(best, filteredRawCandidate);
        best = better(best, filteredLayoutCandidate);

        log.info("[{}] OCR candidate scores receiptId={} style={} raw={} layout={} filteredRaw={} filteredLayout={} selected={}",
                requestId,
                receiptId,
                style,
                rawCandidate.score,
                layoutCandidate.score,
                filteredRawCandidate.score,
                filteredLayoutCandidate.score,
                best.label);

        return best.items;
    }

    private void applyStyleBias(
            ReceiptStyle style,
            ScoredCandidate rawCandidate,
            ScoredCandidate layoutCandidate,
            ScoredCandidate filteredRawCandidate,
            ScoredCandidate filteredLayoutCandidate
    ) {
        switch (style) {
            case BAKERY -> {
                layoutCandidate.score += 6;
                filteredLayoutCandidate.score += 4;
            }
            case USTAR_MULTI_LINE -> {
                rawCandidate.score += 6;
                filteredRawCandidate.score += 2;
            }
            case SUPERMARKET -> {
                filteredLayoutCandidate.score += 8;
                filteredRawCandidate.score += 5;
                layoutCandidate.score += 2;
            }
            case GENERIC -> {
                rawCandidate.score += 1;
                layoutCandidate.score += 1;
            }
            default -> {
            }
        }
    }

    private ScoredCandidate better(ScoredCandidate current, ScoredCandidate challenger) {
        if (challenger.score > current.score) {
            return challenger;
        }
        if (challenger.score == current.score && challenger.items.size() > current.items.size()) {
            return challenger;
        }
        return current;
    }

    private int score(List<ReceiptItemEntity> items) {

        if (items == null || items.isEmpty()) {
            return 0;
        }

        int score = 0;

        for (ReceiptItemEntity item : items) {
            if (item.getAmount() != null &&
                    item.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                score += 4;
            }

            if (item.getQuantity() != null) {
                score += 2;
            }

            if (hasGoodItemName(item.getItemName())) {
                score += 3;
            }

            if (looksLikeBadItemName(item.getItemName())) {
                score -= 5;
            }

            if (item.getRawLine() != null && item.getRawLine().contains("  ")) {
                score += 1;
            }
        }

        return score;
    }

    private boolean hasGoodItemName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return false;
        }

        String upper = itemName.toUpperCase();

        if (upper.length() < 3) {
            return false;
        }

        if (!upper.matches(".*[A-Z].*")) {
            return false;
        }

        return !looksLikeBadItemName(itemName);
    }

    private boolean looksLikeBadItemName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return true;
        }

        String upper = itemName.trim().toUpperCase();

        return upper.contains("TOTAL")
                || upper.contains("GST")
                || upper.contains("PAYMENT")
                || upper.contains("APPROVAL")
                || upper.contains("CARD")
                || upper.contains("LINKPTS")
                || upper.contains("DISCOUNT")
                || upper.contains("SUBTOTAL")
                || upper.contains("NETS")
                || upper.contains("TRANSACTION")
                || upper.contains("TERMINAL")
                || upper.contains("AUTH.")
                || upper.contains("REMARK")
                || upper.contains("DESCRIPTION")
                || upper.contains("RATE")
                || upper.contains("THANK YOU");
    }

    private String maybeFilterByStyle(String text, ReceiptStyle style) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return switch (style) {
            case SUPERMARKET -> filterSupermarketItems(text);
            case BAKERY -> filterBakeryItems(text);
            case USTAR_MULTI_LINE -> filterUstarItems(text);
            default -> text;
        };
    }

    private ReceiptStyle detectReceiptStyle(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ReceiptStyle.GENERIC;
        }

        String text = rawText.toUpperCase();

        if (text.contains("U STARS") || text.contains("USTAR") || text.contains("NAME/ITEMNO")) {
            return ReceiptStyle.USTAR_MULTI_LINE;
        }

        if (text.contains("SWEE HENG") || text.contains("ROTI BOY") || text.contains("NET AMT")) {
            return ReceiptStyle.BAKERY;
        }

        if (text.contains("NTUC") || text.contains("FAIRPRICE") || text.contains("LINK CARD")) {
            return ReceiptStyle.SUPERMARKET;
        }

        return ReceiptStyle.GENERIC;
    }

    private String filterSupermarketItems(String text) {
        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        boolean started = false;

        for (String line : lines) {
            String value = normalize(line);
            if (value.isBlank()) {
                continue;
            }

            String upper = value.toUpperCase();

            if (!started) {
                if (upper.contains("ITEM NAME")
                        || upper.contains("NAME/ITEMNO")
                        || looksLikeItemLine(value)) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (isFooterLine(upper)) {
                break;
            }

            if (shouldSkipLine(value, upper)) {
                continue;
            }

            result.add(value);
        }

        return String.join("\n", result).trim();
    }

    private String filterBakeryItems(String text) {
        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        boolean started = false;

        for (String line : lines) {
            String value = normalize(line);
            if (value.isBlank()) {
                continue;
            }

            String upper = value.toUpperCase();

            if (!started) {
                if (upper.contains("ITEM NAME") || upper.contains("NET AMT")) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (upper.startsWith("SUBTOTAL")
                    || upper.startsWith("GST")
                    || upper.startsWith("TOTAL:")
                    || upper.startsWith("NETS")
                    || upper.contains("MERCHANT ID")
                    || upper.contains("TERMINAL ID")
                    || upper.contains("TRANS DATE/TIME")
                    || upper.contains("TRANSACTION AMOUNT")) {
                break;
            }

            if (shouldSkipLine(value, upper)) {
                continue;
            }

            result.add(value);
        }

        return String.join("\n", result).trim();
    }

    private String filterUstarItems(String text) {
        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        boolean started = false;

        for (String line : lines) {
            String value = normalize(line);
            if (value.isBlank()) {
                continue;
            }

            String upper = value.toUpperCase();

            if (!started) {
                if (upper.contains("NAME/ITEMNO")) {
                    started = true;
                } else {
                    continue;
                }
            }

            if (upper.startsWith("QTY:")
                    || upper.startsWith("ORIGINALAMOUNT")
                    || upper.startsWith("DISCOUNTAMOUNT")
                    || upper.startsWith("SPECIALAMOUNT")
                    || upper.startsWith("TOTAL:")
                    || upper.startsWith("GST 9% INCL")
                    || upper.startsWith("PAY:")
                    || upper.startsWith("REMARK")
                    || upper.startsWith("GST REG NO")
                    || upper.contains("ANY FEEDBACK")) {
                break;
            }

            if (HEADER_DECORATOR_PATTERN.matcher(value).matches()) {
                continue;
            }

            result.add(value);
        }

        return String.join("\n", result).trim();
    }

    private boolean shouldSkipLine(String value, String upper) {
        if (value.isBlank()) {
            return true;
        }

        if (BARCODE_PATTERN.matcher(value).matches()) {
            return true;
        }

        if (HEADER_DECORATOR_PATTERN.matcher(value).matches()) {
            return true;
        }

        if (upper.equals("ITEM NAME")
                || upper.equals("QTY")
                || upper.equals("PRICE")
                || upper.equals("TOTAL")
                || upper.equals("NET AMT")
                || upper.equals("INC GST")
                || upper.equals("NAME/ITEMNO")) {
            return true;
        }

        if (upper.contains("LINK CARD")
                || upper.contains("TOTAL SAVINGS")
                || upper.contains("DESCRIPTION")
                || upper.contains("AFTER GST TX AMNT")
                || upper.contains("ELIGIBLE:")
                || upper.contains("ACNT NO.")
                || upper.contains("LINKPTS")
                || upper.contains("SHAREHOLDER")
                || upper.contains("APPROVED")
                || upper.contains("NO SIGNATURE")
                || upper.contains("EXCHANGE ID")
                || upper.contains("AUTH. CODE")
                || upper.contains("TRANSACTION DATE")) {
            return true;
        }

        return false;
    }

    private boolean isFooterLine(String upper) {
        return upper.startsWith("TOTAL")
                || upper.startsWith("MASTER")
                || upper.startsWith("CARD")
                || upper.startsWith("PAYMENT")
                || upper.startsWith("APPROVAL")
                || upper.startsWith("BATCH")
                || upper.startsWith("REF:")
                || upper.startsWith("TRAN TIME")
                || upper.startsWith("GST")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("TOTAL ITEMS")
                || upper.startsWith("THANK YOU");
    }

    private boolean looksLikeItemLine(String value) {
        String upper = value.toUpperCase();

        if (looksLikeBadItemName(upper)) {
            return false;
        }

        return upper.matches(".*[A-Z].*");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
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

    private enum ReceiptStyle {
        SUPERMARKET,
        BAKERY,
        USTAR_MULTI_LINE,
        GENERIC
    }

    private static class ScoredCandidate {
        private final String label;
        private final List<ReceiptItemEntity> items;
        private int score;

        private ScoredCandidate(String label, List<ReceiptItemEntity> items, int score) {
            this.label = label;
            this.items = items == null ? List.of() : items;
            this.score = score;
        }
    }
}
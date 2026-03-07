package com.moniq.api.parsing;

import com.moniq.api.web.RequestCorrelation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class ReceiptLineFilterService {

    private static final Set<String> NOISE_KEYWORDS = Set.of(
            "gst",
            "tax",
            "change",
            "cash",
            "visa",
            "mastercard",
            "rounding",
            "balance",
            "receipt",
            "store",
            "points",
            "member",
            "loyalty"
    );

    public boolean isNoiseLine(String line) {

        if (line == null || line.isBlank()) {
            return true;
        }

        String normalized = line.toLowerCase();

        for (String keyword : NOISE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                log.warn("[{}] Ignoring OCR noise line={}",
                        RequestCorrelation.getRequestId(), line);
                return true;
            }
        }

        return false;
    }
}
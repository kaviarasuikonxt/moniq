package com.moniq.api.parsing.format;

import org.springframework.stereotype.Component;

@Component
public class DefaultUnknownParsingStrategy implements ReceiptParsingStrategy {

    @Override
    public ReceiptFormat supportedFormat() {
        return ReceiptFormat.UNKNOWN;
    }

    @Override
    public String buildParserInput(ReceiptParsingInput input) {
        if (input.getRawText() != null && !input.getRawText().isBlank()) {
            return input.getRawText();
        }
        return input.getLayoutText();
    }
}
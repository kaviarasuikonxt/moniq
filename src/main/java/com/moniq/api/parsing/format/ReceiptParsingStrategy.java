package com.moniq.api.parsing.format;

import java.util.List;
import com.moniq.api.parsing.ReceiptItemParser;

public interface ReceiptParsingStrategy {

    ReceiptFormat supportedFormat();

    String buildParserInput(ReceiptParsingInput input);

    default List<ReceiptItemParser.ParsedItem> parse(
            ReceiptParsingInput input,
            com.moniq.api.parsing.ReceiptItemParser itemParser
    ) {
        String parserInput = buildParserInput(input);
        return itemParser.parseReceipt(parserInput);
    }
}
package com.moniq.api.parsing.format;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ReceiptParsingStrategyResolver {

    private final Map<ReceiptFormat, ReceiptParsingStrategy> strategies;
    private final DefaultUnknownParsingStrategy defaultUnknownParsingStrategy;

    public ReceiptParsingStrategyResolver(
            List<ReceiptParsingStrategy> strategyList,
            DefaultUnknownParsingStrategy defaultUnknownParsingStrategy
    ) {
        this.defaultUnknownParsingStrategy = defaultUnknownParsingStrategy;
        this.strategies = new EnumMap<>(ReceiptFormat.class);

        for (ReceiptParsingStrategy strategy : strategyList) {
            this.strategies.put(strategy.supportedFormat(), strategy);
        }
    }

    public ReceiptParsingStrategy resolve(ReceiptFormat format) {
        if (format == null) {
            return defaultUnknownParsingStrategy;
        }

        return strategies.getOrDefault(format, defaultUnknownParsingStrategy);
    }
}
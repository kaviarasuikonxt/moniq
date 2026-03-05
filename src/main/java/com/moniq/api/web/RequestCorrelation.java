// src/main/java/com/moniq/api/web/RequestCorrelation.java
package com.moniq.api.web;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestCorrelation {

    private RequestCorrelation() {}

    public static String getRequestId() {
        String id = MDC.get("requestId");
        return (id == null || id.isBlank()) ? "n/a" : id;
    }

    public static void setRequestId(String requestId) {
        MDC.put("requestId", requestId);
    }

    public static void ensureRequestId() {
        if (MDC.get("requestId") == null) {
            MDC.put("requestId", UUID.randomUUID().toString());
        }
    }

    public static void clear() {
        MDC.remove("requestId");
        MDC.remove("userId");
    }
}
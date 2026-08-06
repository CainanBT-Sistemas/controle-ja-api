package com.cainanbt.softwares.controleja.configs;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private CorrelationId() {
    }

    public static String resolve(String headerValue) {
        if (headerValue != null && VALID_PATTERN.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    public static String current() {
        String correlationId = MDC.get(MDC_KEY);
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}

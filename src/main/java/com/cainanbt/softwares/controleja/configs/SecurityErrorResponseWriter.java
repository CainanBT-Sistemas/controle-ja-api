package com.cainanbt.softwares.controleja.configs;

import com.cainanbt.softwares.controleja.exceptions.models.ApiErrorResponse;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

@Slf4j
public final class SecurityErrorResponseWriter {

    public static final String UNAUTHORIZED_MESSAGE = "Token inválido ou expirado.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityErrorResponseWriter() {
    }

    public static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized", UNAUTHORIZED_MESSAGE);
    }

    public static void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden", "Acesso negado.");
    }

    public static void writeClosedTestForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ConstsMessages.CLOSED_TEST_TITLE,
                ConstsMessages.CLOSED_TEST_ACCESS_DENIED
        );
    }

    private static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        String correlationId = CorrelationId.current();
        response.setStatus(status.value());
        response.setHeader(CorrelationId.HEADER_NAME, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        log.warn(
                "api_error method={} path={} status={} exception={}",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                title
        );
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiErrorResponse.builder()
                .code(status.value())
                .title(title)
                .message(message)
                .correlationId(correlationId)
                .build());
    }
}

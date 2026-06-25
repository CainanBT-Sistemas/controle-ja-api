package com.cainanbt.softwares.controleja.configs;

import com.cainanbt.softwares.controleja.exceptions.models.ApiErrorResponse;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

public final class SecurityErrorResponseWriter {

    public static final String UNAUTHORIZED_MESSAGE = "Token inválido ou expirado.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityErrorResponseWriter() {
    }

    public static void writeUnauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", UNAUTHORIZED_MESSAGE);
    }

    public static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden", "Acesso negado.");
    }

    public static void writeClosedTestForbidden(HttpServletResponse response) throws IOException {
        write(
                response,
                HttpStatus.FORBIDDEN,
                ConstsMessages.CLOSED_TEST_TITLE,
                ConstsMessages.CLOSED_TEST_ACCESS_DENIED
        );
    }

    private static void write(HttpServletResponse response, HttpStatus status, String title, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiErrorResponse.builder()
                .code(status.value())
                .title(title)
                .message(message)
                .build());
    }
}

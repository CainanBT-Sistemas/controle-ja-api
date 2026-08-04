package com.cainanbt.softwares.controleja.exceptions.handle;


import com.cainanbt.softwares.controleja.configs.CorrelationId;
import com.cainanbt.softwares.controleja.exceptions.models.ApiErrorResponse;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class RestExceptionHandler {

    /**
     * Converte falhas de regra de negocio para o contrato padrao 400.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handlerBadRequestException(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getTitle(), ex.getDetail(), ex, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handlerForbiddenException(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getTitle(), ex.getDetail(), ex, request);
    }

    /**
     * Agrega erros de validacao de DTO em uma unica resposta 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handlerValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "Erro de Validação", errors, ex, request);
    }

    /**
     * Trata validacoes disparadas em parametros e path variables.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handlerConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        String errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "Erro de Validação", errors, ex, request);
    }

    /**
     * Padroniza parametros obrigatorios ausentes.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handlerMissingParameterException(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Erro de Validação", "Parametro obrigatorio ausente: " + ex.getParameterName(), ex, request);
    }

    /**
     * Padroniza tipos invalidos em query params e path variables, como UUID mal formado.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handlerTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Erro de Validação", "Valor invalido para o parametro: " + ex.getName(), ex, request);
    }

    /**
     * Padroniza JSON invalido ou enum inexistente no corpo da requisicao.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handlerMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Erro de Validação", "Corpo da requisicao invalido ou mal formatado", ex, request);
    }

    /**
     * Converte entidades inexistentes para o contrato padrao 404.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlerEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getTitle(), ex.getMessage(), ex, request);
    }

    /**
     * Garante resposta padronizada para falhas inesperadas sem expor detalhes internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handlerUnexpectedException(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Nao foi possivel processar a requisicao", ex, request);
    }

    /**
     * Monta o payload de erro comum usado por todos os handlers REST.
     */
    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String title,
            String message,
            Exception ex,
            HttpServletRequest request
    ) {
        logError(status, ex, request);
        return new ResponseEntity<>(ApiErrorResponse.builder()
                .code(status.value())
                .title(title)
                .message(message)
                .correlationId(CorrelationId.current())
                .build(), status);
    }

    private void logError(HttpStatus status, Exception ex, HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (status.is5xxServerError()) {
            log.error(
                    "api_error method={} path={} status={} exception={}",
                    method,
                    path,
                    status.value(),
                    ex.getClass().getSimpleName(),
                    ex
            );
            return;
        }
        log.warn(
                "api_error method={} path={} status={} exception={}",
                method,
                path,
                status.value(),
                ex.getClass().getSimpleName()
        );
    }
}

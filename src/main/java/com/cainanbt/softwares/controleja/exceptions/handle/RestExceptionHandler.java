package com.cainanbt.softwares.controleja.exceptions.handle;


import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestExceptionDetails;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.exceptions.models.InternalServerException;
import com.cainanbt.softwares.controleja.exceptions.models.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerBadRequestException(BadRequestException badRequestException){
        String title = badRequestException.getMessage().split("\ndetail:")[0];
        String msg = badRequestException.getMessage().split("\ndetail:")[1];
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .title(title)
                .message(msg)
                .build(),HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerNotFoundException(NotFoundException notFoundException){
        String title = notFoundException.getMessage().split("\ndetail:")[0];
        String msg = notFoundException.getMessage().split("\ndetail:")[1];
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.NOT_FOUND.value())
                .title(title)
                .message(msg)
                .build(),HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerForbiddenException(ForbiddenException forbiddenException){
        String title = forbiddenException.getMessage().split("\ndetail:")[0];
        String msg = forbiddenException.getMessage().split("\ndetail:")[1];
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .title(title)
                .message(msg)
                .build(),HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerInternalServerException(InternalServerException internalServerException){
        String title = internalServerException.getMessage().split("\ndetail:")[0];
        String msg = internalServerException.getMessage().split("\ndetail:")[1];
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .title(title)
                .message(msg)
                .build(),HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Erro ao persistir dados. Verifique se não há violação de restrições do banco de dados.";
        if (ex.getRootCause() != null && ex.getRootCause().getMessage() != null) {
            String rootMessage = ex.getRootCause().getMessage();
            if (rootMessage.contains("duplicate key")) {
                message = "Registro duplicado. Este item já existe no sistema.";
            } else if (rootMessage.contains("foreign key")) {
                message = "Não é possível realizar a operação. Existem registros relacionados.";
            }
        }
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .title("Erro de Integridade de Dados")
                .message(message)
                .build(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .title("Erro de Validação")
                .message(errors)
                .build(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerGenericException(Exception ex) {
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .title("Erro Interno")
                .message("Ocorreu um erro inesperado. Por favor, tente novamente.")
                .build(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

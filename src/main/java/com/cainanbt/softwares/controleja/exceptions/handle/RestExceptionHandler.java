package com.cainanbt.softwares.controleja.exceptions.handle;


import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestExceptionDetails;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundExceptionDetails;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BadRequestExceptionDetails> handlerValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return new ResponseEntity<>(BadRequestExceptionDetails.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .title("Erro de Validação")
                .message(errors)
                .build(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<EntityNotFoundExceptionDetails> handlerEntityNotFoundException(EntityNotFoundException ex) {
        return new ResponseEntity<>(EntityNotFoundExceptionDetails.builder()
                .code(ex.getCode())
                .title(ex.getTitle())
                .message(ex.getMessage())
                .build(), HttpStatus.NOT_FOUND);
    }
}

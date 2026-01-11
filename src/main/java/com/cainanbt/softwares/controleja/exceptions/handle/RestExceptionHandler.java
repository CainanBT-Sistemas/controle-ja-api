package com.cainanbt.softwares.controleja.exceptions.handle;


import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestExceptionDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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
}

package com.cainanbt.softwares.controleja.exceptions.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class InternalServerException extends RuntimeException {
    public InternalServerException(String title, String message) {
        super(title + "\ndetail:" + message);
    }

    public InternalServerException(String title, String message, Throwable cause) {
        super(title + "\ndetail:" + message, cause);
    }
}

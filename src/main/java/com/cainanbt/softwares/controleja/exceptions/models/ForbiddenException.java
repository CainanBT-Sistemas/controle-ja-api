package com.cainanbt.softwares.controleja.exceptions.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String title, String message) {
        super(title + "\ndetail:" + message);
    }
}

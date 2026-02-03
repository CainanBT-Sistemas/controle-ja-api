package com.cainanbt.softwares.controleja.exceptions.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    public NotFoundException(String title, String message) {
        super(title + "\ndetail:" + message);
    }
}

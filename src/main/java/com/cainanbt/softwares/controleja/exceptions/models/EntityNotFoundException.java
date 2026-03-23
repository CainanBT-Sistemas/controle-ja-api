package com.cainanbt.softwares.controleja.exceptions.models;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {
    private final String code;
    private final String title;

    public EntityNotFoundException(String title, String message) {
        super(message);
        this.code = "NOT_FOUND";
        this.title = title;
    }

    public EntityNotFoundException(String code, String title, String message) {
        super(message);
        this.code = code;
        this.title = title;
    }
}

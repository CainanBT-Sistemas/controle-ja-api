package com.cainanbt.softwares.controleja.exceptions.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private final String title;
    private final String detail;

    public BadRequestException(String title, String message) {
        super(title + "\ndetail:" + message);
        this.title = title;
        this.detail = message;
    }

    /**
     * Retorna o titulo de negocio sem depender do formato interno da mensagem.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Retorna a mensagem detalhada de negocio sem depender de parsing por string.
     */
    public String getDetail() {
        return detail;
    }
}

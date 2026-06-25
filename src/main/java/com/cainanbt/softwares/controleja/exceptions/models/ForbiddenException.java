package com.cainanbt.softwares.controleja.exceptions.models;

public class ForbiddenException extends RuntimeException {

    private final String title;
    private final String detail;

    public ForbiddenException(String title, String detail) {
        super(title);
        this.title = title;
        this.detail = detail;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }
}

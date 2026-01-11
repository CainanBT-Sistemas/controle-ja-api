package com.cainanbt.softwares.controleja.exceptions.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BadRequestExceptionDetails {
    private int code;
    private String title;
    private String message;
}

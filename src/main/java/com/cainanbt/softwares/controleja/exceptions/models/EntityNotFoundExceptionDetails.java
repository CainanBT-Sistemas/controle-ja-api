package com.cainanbt.softwares.controleja.exceptions.models;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EntityNotFoundExceptionDetails {
    private String code;
    private String title;
    private String message;
}

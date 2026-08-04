package com.cainanbt.softwares.controleja.exceptions.models;

import lombok.Builder;
import lombok.Getter;

/**
 * Contrato padrao de erro exposto pela API.
 */
@Getter
@Builder
public class ApiErrorResponse {
    private int code;
    private String title;
    private String message;
    private String correlationId;
}

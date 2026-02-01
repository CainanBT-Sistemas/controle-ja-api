package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenLoginDTO {
    @NotBlank(message = "O token é obrigatório")
    private String token;
}

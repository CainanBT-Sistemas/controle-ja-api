package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ReactivationConfirmDTO {
    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "O código é obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código deve possuir 6 dígitos")
    private String code;
}

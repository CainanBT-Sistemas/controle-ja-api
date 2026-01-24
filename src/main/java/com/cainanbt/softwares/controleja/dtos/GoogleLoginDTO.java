package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginDTO {
    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "O ID do Google é obrigatório")
    private String googleId;

    private String displayName;
    private String photoUrl;
}

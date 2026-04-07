package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileDTO {

    @NotBlank(message = "O nome de usuário é obrigatório")
    private String username;
}
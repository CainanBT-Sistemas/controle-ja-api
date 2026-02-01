package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserLoginDTO {
    @NotBlank(message = "O email é obrigatório")
    private String email;
    @NotBlank(message = "A senha é obrigatória")
    private String password;
    private String refreshToken;
}
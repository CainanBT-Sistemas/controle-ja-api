package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginDTO {
    private String email;

    private String googleId;

    @NotBlank(message = "O token Google é obrigatório")
    private String idToken;

    private String displayName;
    private String photoUrl;
}

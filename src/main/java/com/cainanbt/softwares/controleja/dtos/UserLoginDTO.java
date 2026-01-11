package com.cainanbt.softwares.controleja.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserLoginDTO {
    private String email;
    private String password;
    private String refreshToken;
}
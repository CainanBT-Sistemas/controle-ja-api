package com.cainanbt.softwares.controleja.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.UUID;

@Getter
@Data
@AllArgsConstructor
public class UserUpdateTokenDTO {
    private UUID id;
    private String refreshToken;
    private long refreshExpiration;
}

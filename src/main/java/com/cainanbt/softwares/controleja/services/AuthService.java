package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.TokenLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

public interface AuthService {

    /**
     * Valida credenciais locais e emite access token e refresh token.
     */
    UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request) throws BadRequestException;

    /**
     * Autentica um usuario pelo provedor Google, criando a conta quando necessario.
     */
    UserResponseDTO loginGoogle(GoogleLoginDTO googleLogin, HttpServletRequest request);

    /**
     * Gera uma nova sessao a partir de refresh token valido e persistido.
     */
    UserResponseDTO loginAuto(@Valid TokenLoginDTO tokenLoginDTO, HttpServletRequest request);
}

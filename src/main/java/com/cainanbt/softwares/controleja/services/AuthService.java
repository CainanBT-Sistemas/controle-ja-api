package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.TokenLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

public interface AuthService {

    UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request) throws BadRequestException;

    UserResponseDTO loginGoogle(GoogleLoginDTO googleLogin, HttpServletRequest request);

    UserResponseDTO loginAuto(@Valid TokenLoginDTO tokenLoginDTO, HttpServletRequest request);
}

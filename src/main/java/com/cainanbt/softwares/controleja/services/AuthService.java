package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request) throws BadRequestException;

    UserResponseDTO loginGoogle(GoogleLoginDTO googleLogin, HttpServletRequest request);
}

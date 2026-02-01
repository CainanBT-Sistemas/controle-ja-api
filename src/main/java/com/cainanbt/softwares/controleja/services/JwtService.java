package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;

public interface JwtService {
    String generateAccessToken(UserAuthenticateDTO userAuthenticate);
    String generateRefreshToken(UserAuthenticateDTO userAuthenticate);
    long getRefreshExpiration();
    boolean validateToken(String token, UserAuthenticateDTO userAuthenticate);
    String validateToken(String token);

    boolean isValidTokenToLogin(String token);

    UserAuthenticateDTO getUserAuthenticateFromRefreshToken(String refreshToken);
}

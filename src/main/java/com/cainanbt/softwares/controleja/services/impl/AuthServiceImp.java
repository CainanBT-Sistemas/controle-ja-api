package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.UserResponseDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.dtos.AuthResponseDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.services.AuthService;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthServiceImp implements AuthService {

    private final UsersService UserService;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtServiceImp jwtService;

    public AuthServiceImp(UsersService UserService, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtServiceImp jwtService) {
        this.UserService = UserService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request){
        Optional<Users> userOptional = UserService.getUserByEmail(loginAdapter.getEmail());
        if(userOptional.isEmpty() || !passwordEncoder.matches(loginAdapter.getPassword(), userOptional.get().getPassword())){
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED,ConstsMessages.WRONG_LOGIN_CREDENTIALS);
        }
        Users user = userOptional.get();
        if(!user.getEnabled() || !user.getAccountNonLocked()){
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED,ConstsMessages.BLOCKED_USER);
        }

        var authenticate = new UsernamePasswordAuthenticationToken(loginAdapter.getEmail(), loginAdapter.getPassword());
        var auth = this.authenticationManager.authenticate(authenticate);
        UserAuthenticateDTO userAuthenticate = (UserAuthenticateDTO) auth.getPrincipal();

        String accessToken = jwtService.generateAccessToken(userAuthenticate);
        String refreshToken = jwtService.generateRefreshToken(userAuthenticate);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        UserService.updateTokens(new UserUpdateTokenDTO(user.getId(),authResponse.getRefreshToken(), jwtService.getRefreshExpiration()));
        return new UserResponseDTO(ID.toString(user.getId()),user.getUsername(),user.getEmail(),user.getCreatedAt(),authResponse);

    }
}

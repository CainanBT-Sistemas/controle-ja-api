package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TokenLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AuthResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.AuthService;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuthServiceImp implements AuthService {

    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtServiceImp jwtService;

    @Override
    public UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request) {
        Optional<Users> userOptional = usersService.getUserByEmail(loginAdapter.getEmail());
        if (userOptional.isEmpty() || !passwordEncoder.matches(loginAdapter.getPassword(), userOptional.get().getPassword())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.WRONG_LOGIN_CREDENTIALS);
        }
        Users user = userOptional.get();
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.BLOCKED_USER);
        }

        var authenticate = new UsernamePasswordAuthenticationToken(loginAdapter.getEmail(), loginAdapter.getPassword());
        var auth = this.authenticationManager.authenticate(authenticate);
        UserAuthenticateDTO userAuthenticate = (UserAuthenticateDTO) auth.getPrincipal();

        String accessToken = jwtService.generateAccessToken(userAuthenticate);
        String refreshToken = jwtService.generateRefreshToken(userAuthenticate);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        usersService.updateTokens(new UserUpdateTokenDTO(user.getId(), authResponse.getRefreshToken(), jwtService.getRefreshExpiration()));
        return new UserResponseDTO(ID.toString(user.getId()), user.getUsername(), user.getEmail(), user.getCreatedAt(), authResponse);
    }

    @Override
    public UserResponseDTO loginGoogle(GoogleLoginDTO dto, HttpServletRequest request) {
        Users user;
        Optional<Users> userByEmail = usersService.getUserByEmail(dto.getEmail());
        if (userByEmail.isPresent()) {
            user = userByEmail.get();
            if (!Boolean.TRUE.equals(user.getOauth2User())) {
                user.setOauth2User(true);
                user.setOauth2Provider("GOOGLE");
                user.setOauth2ProviderId(dto.getGoogleId());
            }
        } else {
            InsertUpdateUserDTO newUserDto = new InsertUpdateUserDTO();
            newUserDto.setEmail(dto.getEmail());
            newUserDto.setUsername(dto.getDisplayName() != null ? dto.getDisplayName() : "Usuário Google");
            newUserDto.setPassword(java.util.UUID.randomUUID().toString());
            user = usersService.createNewUser(newUserDto, request);
            user.setOauth2User(true);
            user.setOauth2Provider("GOOGLE");
            user.setOauth2ProviderId(dto.getGoogleId());
        }
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.BLOCKED_USER);
        }
        UserAuthenticateDTO userAuthDTO = new UserAuthenticateDTO(user);
        String refreshToken = jwtService.generateRefreshToken(userAuthDTO);
        String accessToken = jwtService.generateAccessToken(userAuthDTO);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        usersService.updateTokens(new UserUpdateTokenDTO(user.getId(), authResponse.getRefreshToken(), jwtService.getRefreshExpiration()));
        return new UserResponseDTO(ID.toString(user.getId()), user.getUsername(), user.getEmail(), user.getCreatedAt(), authResponse);
    }

    @Override
    public UserResponseDTO loginAuto(TokenLoginDTO tokenLoginDTO, HttpServletRequest request) {
        String refreshToken = tokenLoginDTO.getToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        if (!jwtService.isValidTokenToLogin(refreshToken)) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        UserAuthenticateDTO userAuthDTO = jwtService.getUserAuthenticateFromRefreshToken(refreshToken);
        if (userAuthDTO == null) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        Optional<Users> userOptional = usersService.getUserByEmail(userAuthDTO.getUser().getEmail());
        if (userOptional.isEmpty()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.FAILURE_TO_FIND_USER);
        }

        Users user = userOptional.get();
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.BLOCKED_USER);
        }

        if (!user.getRefreshToken().equals(refreshToken)) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        if (!user.getId().equals(userAuthDTO.getUser().getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        String accessToken = jwtService.generateAccessToken(userAuthDTO);
        String newRefreshToken = jwtService.generateRefreshToken(userAuthDTO);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, newRefreshToken);
        usersService.updateTokens(new UserUpdateTokenDTO(user.getId(), authResponse.getRefreshToken(), jwtService.getRefreshExpiration()));

        return new UserResponseDTO(ID.toString(user.getId()), user.getUsername(), user.getEmail(), user.getCreatedAt(), authResponse);
    }
}
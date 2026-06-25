package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
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
import com.cainanbt.softwares.controleja.services.ClosedTestAccessPolicy;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import com.cainanbt.softwares.controleja.services.GoogleIdTokenValidator;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuthServiceImp implements AuthService {

    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtServiceImp jwtService;
    private final GoogleIdTokenValidator googleIdTokenValidator;
    private final EntitlementService entitlementService;
    private final ClosedTestAccessPolicy closedTestAccessPolicy;

    /**
     * Autentica por email e senha, valida situacao cadastral e rotaciona o refresh token.
     */
    @Override
    @Transactional
    public UserResponseDTO login(UserLoginDTO loginAdapter, HttpServletRequest request) {
        String email = loginAdapter.getEmail().trim().toLowerCase();
        Optional<Users> userOptional = usersService.getUserByEmail(email);
        if (userOptional.isEmpty() || !passwordEncoder.matches(loginAdapter.getPassword(), userOptional.get().getPassword())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.WRONG_LOGIN_CREDENTIALS);
        }
        Users user = userOptional.get();
        closedTestAccessPolicy.requireAccess(user.getEmail());
        validateActiveUser(user);

        var authenticate = new UsernamePasswordAuthenticationToken(email, loginAdapter.getPassword());
        var auth = this.authenticationManager.authenticate(authenticate);
        UserAuthenticateDTO userAuthenticate = (UserAuthenticateDTO) auth.getPrincipal();

        return buildAuthenticatedResponse(user, userAuthenticate);
    }

    /**
     * Autentica pelo Google, vinculando conta existente ou criando usuario novo quando necessario.
     */
    @Override
    @Transactional
    public UserResponseDTO loginGoogle(GoogleLoginDTO dto, HttpServletRequest request) {
        Users user;
        GoogleIdentityDTO googleIdentity = googleIdTokenValidator.validate(dto.getIdToken());
        String email = googleIdentity.email();
        closedTestAccessPolicy.requireAccess(email);
        Optional<Users> userByEmail = usersService.getUserByEmail(email);
        if (userByEmail.isPresent()) {
            user = userByEmail.get();
            associateGoogleIdentity(user, googleIdentity);
        } else {
            InsertUpdateUserDTO newUserDto = new InsertUpdateUserDTO();
            newUserDto.setEmail(email);
            newUserDto.setUsername(resolveGoogleDisplayName(googleIdentity.displayName()));
            newUserDto.setPassword(java.util.UUID.randomUUID().toString());
            user = usersService.createNewUser(newUserDto, request);
            user.setOauth2User(true);
            user.setOauth2Provider("GOOGLE");
            user.setOauth2ProviderId(googleIdentity.subject());
        }
        validateActiveUser(user);
        return buildAuthenticatedResponse(user, new UserAuthenticateDTO(user));
    }

    /**
     * Associa uma identidade Google validada sem confiar nos dados enviados pelo cliente.
     */
    private void associateGoogleIdentity(Users user, GoogleIdentityDTO googleIdentity) {
        if (Boolean.TRUE.equals(user.getOauth2User())) {
            boolean sameProvider = "GOOGLE".equalsIgnoreCase(user.getOauth2Provider());
            boolean sameSubject = googleIdentity.subject().equals(user.getOauth2ProviderId());
            if (!sameProvider || !sameSubject) {
                throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.GOOGLE_EMAIL_CONFLICT);
            }
            return;
        }

        user.setOauth2User(true);
        user.setOauth2Provider("GOOGLE");
        user.setOauth2ProviderId(googleIdentity.subject());
    }

    private String resolveGoogleDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return "Usuário Google";
    }

    /**
     * Valida refresh token persistido, rotaciona tokens e devolve nova sessao.
     */
    @Override
    @Transactional
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
        validateActiveUser(user);
        if (!closedTestAccessPolicy.isAccessAllowed(user.getEmail())) {
            usersService.invalidateRefreshToken(user.getId());
            closedTestAccessPolicy.requireAccess(user.getEmail());
        }

        if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        if (!user.getId().equals(userAuthDTO.getUser().getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_TOKEN);
        }

        return buildAuthenticatedResponse(user, userAuthDTO);
    }

    /**
     * Garante que contas desativadas ou bloqueadas nao recebam tokens.
     */
    private void validateActiveUser(Users user) {
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.BLOCKED_USER);
        }
    }

    /**
     * Emite tokens, persiste o refresh token atual e monta o contrato de resposta.
     */
    private UserResponseDTO buildAuthenticatedResponse(Users user, UserAuthenticateDTO userAuthenticate) {
        String accessToken = jwtService.generateAccessToken(userAuthenticate);
        String refreshToken = jwtService.generateRefreshToken(userAuthenticate);
        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        usersService.updateTokens(new UserUpdateTokenDTO(user.getId(), authResponse.getRefreshToken(), jwtService.getRefreshExpiration()));
        return new UserResponseDTO(
                ID.toString(user.getId()),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                authResponse,
                entitlementService.buildForUser(user)
        );
    }
}

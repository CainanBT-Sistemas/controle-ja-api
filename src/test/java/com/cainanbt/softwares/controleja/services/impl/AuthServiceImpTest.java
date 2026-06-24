package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import com.cainanbt.softwares.controleja.services.GoogleIdTokenValidator;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImpTest {

    private static final GoogleIdentityDTO GOOGLE_IDENTITY = new GoogleIdentityDTO(
            "google-subject",
            "google@test.com",
            "Google User",
            "https://example.com/photo.png"
    );

    @Mock
    private UsersService usersService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtServiceImp jwtService;
    @Mock
    private GoogleIdTokenValidator googleIdTokenValidator;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private HttpServletRequest request;

    private AuthServiceImp authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImp(usersService, passwordEncoder, authenticationManager, jwtService, googleIdTokenValidator, entitlementService);
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        lenient().when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        lenient().when(jwtService.getRefreshExpiration()).thenReturn(123456L);
        lenient().when(usersService.updateTokens(any())).thenAnswer(invocation -> userWithGoogleIdentity());
        lenient().when(entitlementService.buildForUser(any())).thenReturn(null);
    }

    @Test
    void shouldCreateUserFromValidatedGoogleToken() {
        when(googleIdTokenValidator.validate("valid-id-token")).thenReturn(GOOGLE_IDENTITY);
        when(usersService.getUserByEmail("google@test.com")).thenReturn(Optional.empty());
        when(usersService.createNewUser(any(), any())).thenReturn(userWithoutGoogleIdentity());

        UserResponseDTO response = authService.loginGoogle(request("client@fake.com", "fake-google-id", "valid-id-token"), request);

        ArgumentCaptor<InsertUpdateUserDTO> userCaptor = ArgumentCaptor.forClass(InsertUpdateUserDTO.class);
        verify(usersService).createNewUser(userCaptor.capture(), any());
        assertEquals("google@test.com", userCaptor.getValue().getEmail());
        assertEquals("Google User", userCaptor.getValue().getUsername());
        assertEquals("google@test.com", response.getEmail());
        assertEquals("access-token", response.getTokens().getAccessToken());
        assertEquals("refresh-token", response.getTokens().getRefreshToken());
    }

    @Test
    void shouldAssociateExistingPasswordUserWhenGoogleEmailIsValidated() {
        Users user = userWithoutGoogleIdentity();
        when(googleIdTokenValidator.validate("valid-id-token")).thenReturn(GOOGLE_IDENTITY);
        when(usersService.getUserByEmail("google@test.com")).thenReturn(Optional.of(user));

        authService.loginGoogle(request("client@fake.com", "fake-google-id", "valid-id-token"), request);

        assertTrue(user.getOauth2User());
        assertEquals("GOOGLE", user.getOauth2Provider());
        assertEquals("google-subject", user.getOauth2ProviderId());
        verify(usersService, never()).createNewUser(any(), any());
    }

    @Test
    void shouldRejectExistingGoogleUserWithDifferentSubject() {
        Users user = userWithGoogleIdentity();
        user.setOauth2ProviderId("another-google-subject");
        when(googleIdTokenValidator.validate("valid-id-token")).thenReturn(GOOGLE_IDENTITY);
        when(usersService.getUserByEmail("google@test.com")).thenReturn(Optional.of(user));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.loginGoogle(request("google@test.com", "another-google-subject", "valid-id-token"), request)
        );

        assertEquals(ConstsMessages.GOOGLE_EMAIL_CONFLICT, ex.getDetail());
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void shouldReturnSameUserOnRecurringGoogleLogin() {
        Users user = userWithGoogleIdentity();
        when(googleIdTokenValidator.validate("valid-id-token")).thenReturn(GOOGLE_IDENTITY);
        when(usersService.getUserByEmail("google@test.com")).thenReturn(Optional.of(user));

        UserResponseDTO response = authService.loginGoogle(request("google@test.com", "google-subject", "valid-id-token"), request);

        assertEquals(ID.toString(user.getId()), response.getId());
        assertEquals("google@test.com", response.getEmail());
        verify(usersService, never()).createNewUser(any(), any());
    }

    @Test
    void shouldRejectInvalidGoogleToken() {
        when(googleIdTokenValidator.validate("invalid-token"))
                .thenThrow(new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_GOOGLE_TOKEN));

        assertThrows(BadRequestException.class, () -> authService.loginGoogle(request("google@test.com", "google-subject", "invalid-token"), request));

        verify(usersService, never()).getUserByEmail(anyString());
        verify(jwtService, never()).generateAccessToken(any());
    }

    private GoogleLoginDTO request(String email, String googleId, String idToken) {
        GoogleLoginDTO dto = new GoogleLoginDTO();
        dto.setEmail(email);
        dto.setGoogleId(googleId);
        dto.setIdToken(idToken);
        dto.setDisplayName("Client Supplied Name");
        dto.setPhotoUrl("https://client.example.com/photo.png");
        return dto;
    }

    private Users userWithoutGoogleIdentity() {
        return baseUser(false, null, null);
    }

    private Users userWithGoogleIdentity() {
        return baseUser(true, "GOOGLE", "google-subject");
    }

    private Users baseUser(Boolean oauth2User, String provider, String providerId) {
        UUID id = ID.generate();
        return Users.builder()
                .id(id)
                .username("Google User")
                .email("google@test.com")
                .password("encoded-password")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .role("USER")
                .oauth2User(oauth2User)
                .oauth2Provider(provider)
                .oauth2ProviderId(providerId)
                .createdAt(DateUtils.getEpochNow())
                .build();
    }
}

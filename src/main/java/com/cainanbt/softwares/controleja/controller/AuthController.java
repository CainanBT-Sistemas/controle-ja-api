package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.TokenLoginDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("controle_ja_api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Autentica o usuario com email e senha e devolve os tokens de acesso.
     */
    @PostMapping
    public ResponseEntity<?> login(@RequestBody @Valid UserLoginDTO userLoginAdapter, HttpServletRequest request) {
        return ResponseEntity.ok().body(authService.login(userLoginAdapter, request));
    }

    /**
     * Autentica ou cria o usuario a partir dos dados enviados pelo provedor Google.
     */
    @PostMapping("/google")
    public ResponseEntity<?> loginGoogle(@RequestBody @Valid GoogleLoginDTO googleDto, HttpServletRequest request) {
        return ResponseEntity.ok().body(authService.loginGoogle(googleDto, request));
    }

    /**
     * Renova a sessao usando um refresh token valido e ainda vinculado ao usuario.
     */
    @PostMapping("/auto-login")
    public ResponseEntity<?> loginAuto(@RequestBody @Valid TokenLoginDTO tokenLoginDTO, HttpServletRequest request) {
        return ResponseEntity.ok().body(authService.loginAuto(tokenLoginDTO, request));
    }
}

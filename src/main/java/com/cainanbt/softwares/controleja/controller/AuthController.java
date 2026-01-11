package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.services.impl.AuthServiceImp;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("controle_ja_api/v1/auth")
public class AuthController {

    private final AuthServiceImp authService;

    public AuthController(AuthServiceImp authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody UserLoginDTO userLoginAdapter, HttpServletRequest request){
        return ResponseEntity.ok().body(authService.login(userLoginAdapter,request));
    }
}

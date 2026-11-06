package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.EmailVerificationConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.EmailVerificationRequestDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountRecoveryRequestResponseDTO;
import com.cainanbt.softwares.controleja.services.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("controle_ja_api/v1/auth/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/request")
    public ResponseEntity<AccountRecoveryRequestResponseDTO> request(
            @RequestBody @Valid EmailVerificationRequestDTO body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(emailVerificationService.request(body, request.getRemoteAddr()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(
            @RequestBody @Valid EmailVerificationConfirmDTO body
    ) {
        emailVerificationService.confirm(body);
        return ResponseEntity.ok(Map.of("message", "E-mail confirmado. Entre na sua conta."));
    }
}

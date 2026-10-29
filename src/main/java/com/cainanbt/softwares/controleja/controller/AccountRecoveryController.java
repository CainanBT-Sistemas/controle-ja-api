package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.AccountRecoveryRequestDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordResetConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.ReactivationConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountRecoveryRequestResponseDTO;
import com.cainanbt.softwares.controleja.services.AccountReactivationService;
import com.cainanbt.softwares.controleja.services.AccountRecoveryService;
import com.cainanbt.softwares.controleja.services.PasswordRecoveryService;
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
@RequestMapping("controle_ja_api/v1/auth")
public class AccountRecoveryController {
    private final AccountRecoveryService accountRecoveryService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AccountReactivationService accountReactivationService;

    @PostMapping("/recovery/request")
    public ResponseEntity<AccountRecoveryRequestResponseDTO> request(
            @RequestBody @Valid AccountRecoveryRequestDTO body,
            HttpServletRequest request
    ) {
        AccountRecoveryRequestResponseDTO response = accountRecoveryService.request(body, request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @RequestBody @Valid PasswordResetConfirmDTO body
    ) {
        passwordRecoveryService.confirm(body);
        return ResponseEntity.ok(Map.of("message", "Senha redefinida. Entre novamente."));
    }

    @PostMapping("/reactivation/confirm")
    public ResponseEntity<Map<String, String>> confirmReactivation(
            @RequestBody @Valid ReactivationConfirmDTO body
    ) {
        accountReactivationService.confirm(body);
        return ResponseEntity.ok(Map.of("message", "Conta reativada. Entre novamente."));
    }
}

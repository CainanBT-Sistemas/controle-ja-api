package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.services.AccountsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/accounts")
public class AccountsController {
    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.createAccount(accountDTO)));
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        return ResponseEntity.ok(accountsService.listMyAccounts().stream().map(AccountResponseDTO::toDTO).toList());
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> update(@PathVariable UUID accountId, @RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.updateAccount(accountId, accountDTO)));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<?> delete(@PathVariable UUID accountId) {
        accountsService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}

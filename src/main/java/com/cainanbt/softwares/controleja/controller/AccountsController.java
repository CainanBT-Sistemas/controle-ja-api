package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.BalanceAdjustmentDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/accounts")
public class AccountsController {
    private final AccountsService accountsService;
    private final TransactionService transactionService;

    public AccountsController(AccountsService accountsService, TransactionService transactionService) {
        this.accountsService = accountsService;
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.createAccount(accountDTO)));
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        return ResponseEntity.ok(accountsService.listMyAccountsExceptCrediCard().stream().map(AccountResponseDTO::toDTO).toList());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.findByIdOrThrow(id)));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.updateAccount(id, accountDTO)));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        accountsService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/adjust")
    public ResponseEntity<?> adjustBalance(@PathVariable UUID id, @RequestBody @Valid BalanceAdjustmentDTO dto) {
        transactionService.adjustBalance(id, dto.getNewBalance());
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.BALANCE_ADJUSTMENT_SUCCESS);
        return ResponseEntity.ok(response);
    }
}

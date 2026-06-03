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
import java.util.List;
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

    /**
     * Cria uma conta financeira comum para o usuário autenticado.
     */
    @PostMapping
    public ResponseEntity<AccountResponseDTO> create(@RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.createAccount(accountDTO)));
    }

    /**
     * Lista contas do usuário que podem ser usadas em lançamentos e saldo, sem contas espelho de cartão.
     */
    @GetMapping
    public ResponseEntity<List<AccountResponseDTO>> listAll() {
        return ResponseEntity.ok(accountsService.listMyAccountsExceptCrediCard().stream().map(AccountResponseDTO::toDTO).toList());
    }

    /**
     * Consulta uma conta ativa do usuário autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.findMyAccountById(id)));
    }

    /**
     * Atualiza dados cadastrais da conta sem alterar o saldo corrente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid AccountDTO accountDTO) {
        return ResponseEntity.ok(AccountResponseDTO.toDTO(accountsService.updateAccount(id, accountDTO)));
    }

    /**
     * Remove logicamente a conta quando ela não é a conta principal do usuário.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
        accountsService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }

    /**
     * Ajusta o saldo criando uma transação de compensação para manter rastreabilidade financeira.
     */
    @PutMapping("/{id}/adjust")
    public ResponseEntity<Map<String, String>> adjustBalance(@PathVariable UUID id, @RequestBody @Valid BalanceAdjustmentDTO dto) {
        transactionService.adjustBalance(id, dto.getNewBalance());
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.BALANCE_ADJUSTMENT_SUCCESS);
        return ResponseEntity.ok(response);
    }
}

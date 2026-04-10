package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.responses.TransactionResponseDTO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponseDTO> create(@RequestBody @Valid TransactionDTO dto) {
        var entity = transactionService.createTransaction(dto);
        return ResponseEntity.ok(TransactionResponseDTO.toDTO(entity));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponseDTO>> listAll(@RequestParam Long start, @RequestParam Long end) {
        var list = transactionService.listLastTransactionsDTO(start, end);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(TransactionResponseDTO.toDTO(transactionService.findByIdOrThrow(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid TransactionDTO dto,
            @RequestParam(defaultValue = "false") Boolean updateFuture) {

        var dtoResponse = transactionService.updateTransactionDTO(id, dto, updateFuture);
        return ResponseEntity.ok(dtoResponse);
    }

    // NOVO: Recebe o parâmetro cancelFuture
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") Boolean cancelFuture) {

        transactionService.softDelete(id, cancelFuture);

        Map<String, String> response = new HashMap<>();
        response.put("message", cancelFuture ? "Assinatura cancelada e transações futuras removidas." : ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }
}
package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.responses.TransactionResponseDTO;
import com.cainanbt.softwares.controleja.services.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<List<TransactionResponseDTO>> listAll() {
        var list = transactionService.listLastTransactions();
        return ResponseEntity.ok(list.stream().map(TransactionResponseDTO::toDTO).toList());
    }
}

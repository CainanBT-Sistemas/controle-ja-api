package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CreditCardResponseDTO;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/cards")
public class CreditCardController {
    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @PostMapping
    public ResponseEntity<?> createCard(@RequestBody @Valid CreditCardDTO dto) {
        return ResponseEntity.ok(CreditCardResponseDTO.toDTO(creditCardService.createCard(dto)));
    }

    @PutMapping("/{cardId}")
    public ResponseEntity<?> updateCard(@PathVariable UUID cardId, @RequestBody @Valid CreditCardDTO dto) {
        return ResponseEntity.ok(CreditCardResponseDTO.toDTO(creditCardService.updateCard(cardId, dto)));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<?> deleteCard(@PathVariable UUID cardId) {
        creditCardService.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }
    // TODO Futuramente: @GetMapping para listar os cartões no App
}

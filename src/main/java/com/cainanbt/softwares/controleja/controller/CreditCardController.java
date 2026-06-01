package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CreditCardResponseDTO;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("controle_ja_api/v1/cards")
@RequiredArgsConstructor
public class CreditCardController {
    private final CreditCardService creditCardService;

    /**
     * Cria um cartão de crédito e a conta espelho usada nas faturas.
     */
    @PostMapping
    public ResponseEntity<CreditCardResponseDTO> createCard(@RequestBody @Valid CreditCardDTO dto) {
        return ResponseEntity.ok(CreditCardResponseDTO.toDTO(creditCardService.createCard(dto)));
    }

    /**
     * Lista os cartões ativos do usuário autenticado.
     */
    @GetMapping
    public ResponseEntity<List<CreditCardResponseDTO>> listAll() {
        return ResponseEntity.ok(creditCardService.listMyCards().stream().map(CreditCardResponseDTO::toDTO).toList());
    }

    /**
     * Consulta um cartão ativo garantindo propriedade do usuário autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CreditCardResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(CreditCardResponseDTO.toDTO(creditCardService.findMyCardById(id)));
    }

    /**
     * Atualiza cadastro e limite do cartão, preservando o limite já utilizado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CreditCardResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid CreditCardDTO dto) {
        return ResponseEntity.ok(CreditCardResponseDTO.toDTO(creditCardService.updateCard(id, dto)));
    }

    /**
     * Remove logicamente o cartão e sua conta espelho.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        creditCardService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }
}

package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditCardService {
    /**
     * Cria um cartão e sua conta espelho de fatura para o usuário autenticado.
     */
    CreditCard createCard(CreditCardDTO dto);

    /**
     * Lista os cartões ativos pertencentes ao usuário autenticado.
     */
    List<CreditCard> listMyCards();

    /**
     * Localiza o cartão ativo pela conta espelho vinculada.
     */
    CreditCard findByAccountId(UUID accountId);

    /**
     * Persiste alterações de limite feitas por fluxos financeiros.
     */
    void updateLimit(CreditCard card);

    /**
     * Busca cartão ativo por id sem aplicar regra de propriedade.
     */
    Optional<CreditCard> findById(UUID id);

    /**
     * Busca cartão ativo por id ou lança erro de entidade não encontrada.
     */
    CreditCard findByIdOrThrow(UUID id);

    /**
     * Busca cartão ativo garantindo que ele pertence ao usuário autenticado.
     */
    CreditCard findMyCardById(UUID id);

    /**
     * Atualiza dados cadastrais e limite do cartão do usuário autenticado.
     */
    CreditCard updateCard(UUID id, CreditCardDTO dto);

    /**
     * Remove logicamente o cartão e sua conta espelho.
     */
    void softDelete(UUID id);
}

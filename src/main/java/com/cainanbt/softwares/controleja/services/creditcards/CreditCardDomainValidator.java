package com.cainanbt.softwares.controleja.services.creditcards;

import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;

import java.math.BigDecimal;

/**
 * Centraliza as regras de propriedade e integridade dos cartões de crédito.
 */
public class CreditCardDomainValidator {

    private static final int FREE_PLAN_CARD_LIMIT = 2;

    /**
     * Garante que o cartão pertence ao usuário autenticado antes de expor ou alterar dados.
     */
    public void validateOwner(CreditCard card, Users currentUser) {
        if (card == null || card.getUser() == null || currentUser == null
                || !card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CARD);
        }
    }

    /**
     * Bloqueia criação acima do limite permitido para o plano atual.
     */
    public void validateCanCreate(long activeCardsCount) {
        if (activeCardsCount >= FREE_PLAN_CARD_LIMIT) {
            throw new BadRequestException(ConstsMessages.LIMIT_REACHED_TITLE, ConstsMessages.LIMIT_REACHED_CARDS);
        }
    }

    /**
     * Impede reduzir o limite total abaixo do valor já utilizado no cartão.
     */
    public void validateTotalLimitCanCoverUsedAmount(BigDecimal newTotalLimit, BigDecimal usedAmount) {
        if (newTotalLimit != null && usedAmount != null && newTotalLimit.compareTo(usedAmount) < 0) {
            throw new BadRequestException(
                    "Limite Inválido",
                    "O novo limite não pode ser menor que o valor já utilizado na fatura (R$ " + usedAmount + ")."
            );
        }
    }

    /**
     * Bloqueia exclusão repetida de um cartão já removido.
     */
    public void validateCanDelete(CreditCard card) {
        if (card.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }
    }
}

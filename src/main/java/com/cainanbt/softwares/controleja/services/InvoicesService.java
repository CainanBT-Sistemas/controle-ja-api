package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.Invoices;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoicesService {
    /**
     * Persiste uma fatura.
     */
    Invoices save(Invoices invoice);

    /**
     * Persiste faturas em lote quando uma operação altera mais de um vencimento.
     */
    List<Invoices> saveAll(List<Invoices> invoices);

    /**
     * Busca uma fatura pelo id.
     */
    Optional<Invoices> findById(UUID id);

    /**
     * Busca uma fatura pelo id e falha com erro de domínio quando não existe.
     */
    Invoices findByIdOrThrow(UUID id);

    /**
     * Busca uma fatura específica de um cartão por mês e ano.
     */
    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);

    /**
     * Busca faturas futuras pendentes de um cartão após uma data de corte.
     */
    List<Invoices> findFutureUnpaidByCardAndDate(UUID userId, UUID cardId, Long expirationDate);

    /**
     * Busca faturas do usuário em um período.
     */
    List<Invoices> findByUserAndDateBetween(UUID userId, Long start, Long end);
}

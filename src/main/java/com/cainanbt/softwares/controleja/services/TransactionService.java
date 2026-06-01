package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.responses.TransactionResponseDTO;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.OperationScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionService {
    Transactions createTransaction(TransactionDTO dto);

    List<Transactions> listLastTransactions(Long start, Long end);

    Optional<Transactions> findById(UUID id);

    Transactions findByIdOrThrow(UUID id);

    Transactions updateTransaction(UUID id, TransactionDTO dto, OperationScope operationScope);

    void softDelete(UUID id, OperationScope operationScope);

    void cascadeRuleUpdate(UUID ruleId, BigDecimal newAmount);

    void generateProjectionsForRule(RecurrenceRule rule, LocalDate limitDate);

    void adjustBalance(UUID accountId, BigDecimal newBalance);

    void generateProjectionsByRuleId(UUID ruleId, LocalDate limitDate);

    List<TransactionResponseDTO> listLastTransactionsDTO(Long start, Long end);

    TransactionResponseDTO updateTransactionDTO(UUID id, TransactionDTO dto, OperationScope operationScope);

    List<TransactionResponseDTO> getTransactionsTypeVehicle(Long start, Long end);
}

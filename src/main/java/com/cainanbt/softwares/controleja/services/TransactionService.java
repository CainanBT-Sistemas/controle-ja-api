package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionService {
    Transactions createTransaction(TransactionDTO dto);

    List<Transactions> listLastTransactions();
    
    Optional<Transactions> findById(UUID id);
    
    Transactions findByIdOrThrow(UUID id);
    
    Transactions updateTransaction(UUID id, TransactionDTO dto);
    
    void softDelete(UUID id);
}

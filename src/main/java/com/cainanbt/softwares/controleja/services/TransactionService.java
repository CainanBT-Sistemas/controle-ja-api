package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;

import java.util.List;
import java.util.UUID;

public interface TransactionService {
    Transactions createTransaction(TransactionDTO dto);

    List<Transactions> listLastTransactions();

    void deleteTransaction(UUID id);
}

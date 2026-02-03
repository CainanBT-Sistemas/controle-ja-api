package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsService {
    Accounts createAccount(AccountDTO dto);

    Optional<Accounts> findById(UUID id);
    
    Accounts findByIdOrThrow(UUID id);
    
    List<Accounts> listMyAccounts();

    Accounts update(Accounts accounts);
    
    Accounts updateAccount(UUID id, AccountDTO dto);
    
    void softDelete(UUID id);

    Accounts save(Accounts accounts);
}

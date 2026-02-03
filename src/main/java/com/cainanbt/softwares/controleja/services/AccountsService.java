package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;

import java.util.List;
import java.util.UUID;

public interface AccountsService {
    Accounts createAccount(AccountDTO dto);

    List<Accounts> listMyAccounts();

    Accounts updateAccount(UUID accountId, AccountDTO dto);

    void deleteAccount(UUID accountId);
}

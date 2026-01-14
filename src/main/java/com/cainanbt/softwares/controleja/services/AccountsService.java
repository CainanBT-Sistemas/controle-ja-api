package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;

import java.util.List;

public interface AccountsService {
    Accounts createAccount(AccountDTO dto);

    List<Accounts> listMyAccounts();
}

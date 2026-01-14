package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;

    public AccountsServiceImpl(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
    }

    @Override
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        Accounts newAccount = Accounts.builder()
                .id(ID.generate())
                .name(dto.getName())
                .type(dto.getType())
                .institution(dto.getInstitution() != null ? dto.getInstitution() : "N/A") // Tratamento simples
                .currency("BRL") // Hardcoded para MVP
                .currentBalance(dto.getInitialBalance())
                .initialBalance(dto.getInitialBalance())
                .calculateBalance(true)
                .enabled(true)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();

        return accountsRepository.save(newAccount);
    }

    @Override
    public List<Accounts> listMyAccounts() {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        return accountsRepository.findByUserId(user.getId());
    }
}
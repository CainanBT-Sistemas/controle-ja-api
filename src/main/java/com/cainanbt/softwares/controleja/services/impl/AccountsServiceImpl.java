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
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository repository;

    public AccountsServiceImpl(AccountsRepository accountsRepository) {
        this.repository = accountsRepository;
    }

    @Override
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        String institution = dto.getInstitution() != null ? dto.getInstitution() : "N/A";
        Accounts newAccount = Accounts.builder()
                .id(ID.generate())
                .name(dto.getName())
                .type(dto.getType())
                .institution(institution)
                .currency("BRL")
                .currentBalance(dto.getInitialBalance())
                .initialBalance(dto.getInitialBalance())
                .calculateBalance(true)
                .enabled(true)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();

        return repository.save(newAccount);
    }

    @Override
    public Optional<Accounts> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Accounts> listMyAccounts() {
        Users user = SecurityContextUtils.getCurrentUser();

        return repository.findByUserId(user.getId());
    }

    @Override
    public Accounts update(Accounts accounts) {
        if (accounts.getId() == null) {
            throw new BadRequestException("Erro", "IMpossivel atualizar conta sem ID");
        }
        return repository.save(accounts);
    }
}
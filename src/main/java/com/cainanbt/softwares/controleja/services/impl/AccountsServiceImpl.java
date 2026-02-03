package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.exceptions.models.InternalServerException;
import com.cainanbt.softwares.controleja.exceptions.models.NotFoundException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        String institution = dto.getInstitution() != null ? dto.getInstitution() : "N/A";
        
        try {
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
        } catch (Exception e) {
            throw new InternalServerException("Erro ao criar conta", "Não foi possível criar a conta. Tente novamente.", e);
        }
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
    @Transactional
    public Accounts update(Accounts accounts) {
        if (accounts.getId() == null) {
            throw new BadRequestException("Erro", "Impossível atualizar conta sem ID");
        }
        
        Users user = SecurityContextUtils.getCurrentUser();
        Optional<Accounts> existingAccount = repository.findById(accounts.getId());
        
        if (existingAccount.isEmpty()) {
            throw new NotFoundException("Conta não encontrada", "A conta especificada não existe.");
        }
        
        if (!existingAccount.get().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Acesso negado", "Você não tem permissão para atualizar esta conta.");
        }
        
        try {
            return repository.save(accounts);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao atualizar conta", "Não foi possível atualizar a conta. Tente novamente.", e);
        }
    }

    @Override
    @Transactional
    public Accounts save(Accounts accounts) {
        try {
            return repository.save(accounts);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao salvar conta", "Não foi possível salvar a conta. Tente novamente.", e);
        }
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id) {
        Users user = SecurityContextUtils.getCurrentUser();
        
        Accounts account = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada", "A conta especificada não existe."));
        
        if (!account.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Acesso negado", "Você não tem permissão para deletar esta conta.");
        }
        
        try {
            account.setDeletedAt(System.currentTimeMillis());
            repository.save(account);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao deletar conta", "Não foi possível deletar a conta. Tente novamente.", e);
        }
    }
}
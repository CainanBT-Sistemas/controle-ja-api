package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository repository;


    @Override
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        String institution = (dto.getInstitution() != null && !dto.getInstitution().trim().isEmpty())
                ? dto.getInstitution().trim()
                : "";

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
                .icon(dto.getIcon() != null ? dto.getIcon() : "account_balance")
                .color(dto.getColor() != null ? dto.getColor() : "#42A5F5")
                .isDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false)
                .build();

        return repository.save(newAccount);
    }

    @Override
    public Optional<Accounts> findById(UUID id) {
        return repository.findByIdAndNotDeleted(id);
    }
    
    @Override
    public Accounts findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ACCOUNT_NOT_FOUND, 
                    "Conta com ID " + id + " não encontrada ou já foi excluída"));
    }

    @Override
    public List<Accounts> listMyAccounts() {
        Users user = SecurityContextUtils.getCurrentUser();

        return repository.findByUserId(user.getId());
    }

    @Override
    public Accounts updateAccount(UUID id, AccountDTO dto) {
        Accounts account = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para alterar esta conta");
        }

        if (dto.getName() != null) account.setName(dto.getName());
        if (dto.getType() != null) account.setType(dto.getType());
        if (dto.getInstitution() != null) {
            account.setInstitution(dto.getInstitution().trim().isEmpty() ? "" : dto.getInstitution());
        }

        if (dto.getIcon() != null) account.setIcon(dto.getIcon());
        if (dto.getColor() != null) account.setColor(dto.getColor());
        if (dto.getIsDefault() != null) account.setIsDefault(dto.getIsDefault());

        account.setUpdatedAt(System.currentTimeMillis());
        return repository.save(account);
    }
    
    @Override
    public void softDelete(UUID id) {
        Accounts account = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        if (account.getIsDefault()) {
            throw new BadRequestException("Acesso negado", "Você não pode remover a conta principal");
        }

        // Verify ownership
        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para excluir esta conta");
        }
        
        // Check if already deleted
        if (account.getDeletedAt() != null) {
            throw new BadRequestException("Erro", ConstsMessages.ENTITY_ALREADY_DELETED);
        }
        
        // Soft delete
        account.setDeletedAt(System.currentTimeMillis());
        repository.save(account);
    }

    @Override
    public Accounts update(Accounts accounts) {
        if (accounts.getId() == null) {
            throw new BadRequestException("Erro", "IMpossivel atualizar conta sem ID");
        }
        return repository.save(accounts);
    }

    @Override
    public Accounts save(Accounts accounts) {
        return repository.save(accounts);
    }
}
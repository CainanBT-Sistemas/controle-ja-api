package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;

    public TransactionServiceImpl(TransactionRepository transactionRepository, AccountsService accountsService, CategoryService categoryService) {
        this.repository = transactionRepository;
        this.accountsService = accountsService;
        this.categoryService = categoryService;
    }

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getUserLogged().orElseThrow(() -> new BadRequestException("Erro", "Usuário não autenticado"));

        Accounts account = accountsService.findById(dto.getAccountId()).orElseThrow(() -> new BadRequestException("Erro", "Conta não encontrada"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Erro", "Conta inválida");
        }

        Category category = categoryService.findById(dto.getCategoryId())
                .orElseThrow(() -> new BadRequestException("Erro", "Categoria não encontrada"));

        Transactions transaction = Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .paid(dto.getPaid())
                .fixed(false)
                .enabled(true)
                .account(account)
                .category(category)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();

        if (Boolean.TRUE.equals(transaction.getPaid())) {
            updateAccountBalance(account, transaction.getType(), transaction.getAmount());
            accountsService.update(account);
        }
        return repository.save(transaction);
    }

    private void updateAccountBalance(Accounts account, String type, BigDecimal amount) {
        if ("DESPESA".equalsIgnoreCase(type)) {
            account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        } else if ("RECEITA".equalsIgnoreCase(type)) {
            account.setCurrentBalance(account.getCurrentBalance().add(amount));
        }
    }

    @Override
    public List<Transactions> listLastTransactions() {
        Users user = SecurityContextUtils.getUserLogged().orElseThrow(() -> new BadRequestException("Erro", "Usuário não autenticado"));
        return repository.findByUserIdOrderByDateDesc(user.getId());
    }
}

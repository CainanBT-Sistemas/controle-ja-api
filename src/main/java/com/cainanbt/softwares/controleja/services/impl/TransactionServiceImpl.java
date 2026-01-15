package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;

    public TransactionServiceImpl(TransactionRepository transactionRepository, AccountsService accountsService, CategoryService categoryService, CreditCardService creditCardService) {
        this.repository = transactionRepository;
        this.accountsService = accountsService;
        this.categoryService = categoryService;
        this.creditCardService = creditCardService;
    }

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

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

        if ("CREDIT_CARD".equals(account.getType())) {
            return processCreditCardTransaction(dto, account, category, user);
        }

        if (Boolean.TRUE.equals(transaction.getPaid())) {
            processBalanceOrLimit(account, transaction);
            accountsService.update(account);
        }
        return repository.save(transaction);
    }

    private void processBalanceOrLimit(Accounts account, Transactions transaction) {

        if ("CREDIT_CARD".equals(account.getType())) {
            CreditCard card = creditCardService.findByAccountId(account.getId());
            if ("DESPESA".equalsIgnoreCase(transaction.getType())) {
                card.setCurrentLimit(card.getCurrentLimit().subtract(transaction.getAmount()));
                account.setCurrentBalance(account.getCurrentBalance().subtract(transaction.getAmount()));
            } else if ("RECEITA".equalsIgnoreCase(transaction.getType())) {
                card.setCurrentLimit(card.getCurrentLimit().add(transaction.getAmount()));
                account.setCurrentBalance(account.getCurrentBalance().add(transaction.getAmount()));
            }
            if (card.getCurrentLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Erro", "Limite insuficiente!");
            }
            creditCardService.updateLimit(card);
            accountsService.update(account);
        } else {
            if ("DESPESA".equalsIgnoreCase(transaction.getType())) {
                account.setCurrentBalance(account.getCurrentBalance().subtract(transaction.getAmount()));
            } else if ("RECEITA".equalsIgnoreCase(transaction.getType())) {
                account.setCurrentBalance(account.getCurrentBalance().add(transaction.getAmount()));
            }
            accountsService.update(account);
        }
    }

    @Override
    public List<Transactions> listLastTransactions() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdOrderByDateDesc(user.getId());
    }
}

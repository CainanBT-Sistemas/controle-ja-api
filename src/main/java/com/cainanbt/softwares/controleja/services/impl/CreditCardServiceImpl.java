package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CreditCardServiceImpl implements CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final AccountsRepository accountsRepository;

    public CreditCardServiceImpl(CreditCardRepository creditCardRepository, AccountsRepository accountsRepository) {
        this.creditCardRepository = creditCardRepository;
        this.accountsRepository = accountsRepository;
    }

    @Override
    @Transactional
    public CreditCard createCard(CreditCardDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        long totalCards = creditCardRepository.countByUserId(user.getId());
        if (totalCards >= 2) {
            throw new BadRequestException("Limite Atingido", "Usuários Free só podem ter 2 cartões de crédito. Assine o Premium!");
        }

        Accounts cardAccount = Accounts.builder()
                .id(ID.generate())
                .name(dto.getName() + " (Fatura)")
                .type(AccountType.CREDIT_CARD)
                .institution(dto.getName())
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(false)
                .enabled(true)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();

        Accounts savedAccount = accountsRepository.save(cardAccount);

        CreditCard card = CreditCard.builder()
                .id(ID.generate())
                .name(dto.getName())
                .totalLimit(dto.getLimit())
                .currentLimit(dto.getLimit())
                .closeDay(dto.getCloseDay())
                .bestDay(dto.getBestDay())
                .user(user)
                .accounts(savedAccount)
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .build();

        return creditCardRepository.save(card);
    }

    @Override
    public List<CreditCard> listMyCards() {
        Users user = SecurityContextUtils.getCurrentUser();
        return creditCardRepository.findByUserId(user.getId());
    }

    @Override
    public CreditCard findByAccountId(UUID accountId) {
        return creditCardRepository.findByAccountsId(accountId)
                .orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado para esta conta"));
    }

    @Override
    public void updateLimit(CreditCard card) {
        creditCardRepository.save(card);
    }
}
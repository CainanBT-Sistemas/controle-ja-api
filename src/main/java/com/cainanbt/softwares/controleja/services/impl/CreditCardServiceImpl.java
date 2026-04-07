package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class CreditCardServiceImpl implements CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final AccountsRepository accountsRepository;

    @Override
    @Transactional
    public CreditCard createCard(CreditCardDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        long totalCards = creditCardRepository.countByUserId(user.getId());
        if (totalCards >= 2) {
            throw new BadRequestException(ConstsMessages.LIMIT_REACHED_TITLE, ConstsMessages.LIMIT_REACHED_CARDS);
        }

        Accounts cardAccount = Accounts.builder()
                .id(ID.generate())
                .name(dto.getName() + " (Fatura)")
                .type(AccountType.CREDIT_CARD)
                .institution(dto.getName() != null && !dto.getName().trim().isEmpty() ? dto.getName() : "")
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(false)
                .enabled(true)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .icon(dto.getIcon() != null ? dto.getIcon() : "credit_card")
                .color(dto.getColor() != null ? dto.getColor() : "#9C27B0")
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
                .icon(dto.getIcon() != null ? dto.getIcon() : "credit_card")
                .color(dto.getColor() != null ? dto.getColor() : "#9C27B0")
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
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ACCOUNT_NOT_FOUND));
    }

    @Override
    public void updateLimit(CreditCard card) {
        creditCardRepository.save(card);
    }

    @Override
    public Optional<CreditCard> findById(UUID id) {
        return creditCardRepository.findByIdAndNotDeleted(id);
    }

    @Override
    public CreditCard findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.CREDIT_CARD_NOT_FOUND));
    }

    @Override
    @Transactional
    public CreditCard updateCard(UUID id, CreditCardDTO dto) {
        CreditCard card = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CARD);
        }

        if (card.getAccounts() != null) {
            Accounts account = card.getAccounts();
            if (dto.getName() != null) {
                account.setName(dto.getName() + " (Fatura)");
                account.setInstitution(dto.getName());
            }
            if (dto.getIcon() != null) account.setIcon(dto.getIcon());
            if (dto.getColor() != null) account.setColor(dto.getColor());
            accountsRepository.save(account);
        }

        if (dto.getName() != null) card.setName(dto.getName());

        if (dto.getLimit() != null) {
            BigDecimal difference = dto.getLimit().subtract(card.getTotalLimit());
            card.setTotalLimit(dto.getLimit());
            card.setCurrentLimit(card.getCurrentLimit().add(difference));
        }

        if (dto.getCloseDay() > 0) card.setCloseDay(dto.getCloseDay());
        if (dto.getBestDay() > 0) card.setBestDay(dto.getBestDay());

        if (dto.getIcon() != null) card.setIcon(dto.getIcon());
        if (dto.getColor() != null) card.setColor(dto.getColor());

        card.setUpdatedAt(System.currentTimeMillis());

        return creditCardRepository.save(card);
    }

    @Override
    public void softDelete(UUID id) {
        CreditCard card = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CARD);
        }

        if (card.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        if (card.getAccounts() != null) {
            Accounts account = card.getAccounts();
            account.setDeletedAt(System.currentTimeMillis());
            accountsRepository.save(account);
        }

        card.setDeletedAt(System.currentTimeMillis());
        creditCardRepository.save(card);
    }
}
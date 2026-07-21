package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.repositories.InstallmentPlanRepository;
import com.cainanbt.softwares.controleja.repositories.InvoicesRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.creditcards.CreditCardAccountFactory;
import com.cainanbt.softwares.controleja.services.creditcards.CreditCardDomainValidator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class CreditCardServiceImpl implements CreditCardService {

    private final CreditCardDomainValidator creditCardDomainValidator = new CreditCardDomainValidator();
    private final CreditCardAccountFactory creditCardAccountFactory = new CreditCardAccountFactory();

    private final CreditCardRepository creditCardRepository;
    private final AccountsRepository accountsRepository;
    private final InvoicesRepository invoicesRepository;
    private final InstallmentPlanRepository installmentPlanRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Cria o cartão e a conta espelho usada pelo fluxo de faturas.
     */
    @Override
    @Transactional
    public CreditCard createCard(CreditCardDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        long now = DateUtils.getEpochNow();

        creditCardDomainValidator.validateCanCreate(creditCardRepository.countByUserId(user.getId()));
        Accounts savedAccount = accountsRepository.save(creditCardAccountFactory.createInvoiceAccount(dto, user, now));
        CreditCard savedCard = creditCardRepository.save(buildCreditCard(dto, user, savedAccount, now));

        log.info("Credit card created: cardId={}, accountId={}", savedCard.getId(), savedAccount.getId());
        return savedCard;
    }

    /**
     * Retorna somente cartões ativos do usuário autenticado.
     */
    @Override
    public List<CreditCard> listMyCards() {
        Users user = SecurityContextUtils.getCurrentUser();
        return creditCardRepository.findByUserId(user.getId());
    }

    /**
     * Encontra o cartão pela conta espelho usada em transações de cartão.
     */
    @Override
    public CreditCard findByAccountId(UUID accountId) {
        return creditCardRepository.findByAccountsId(accountId)
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ACCOUNT_NOT_FOUND));
    }

    /**
     * Salva alterações de limite geradas por compras, exclusões, pagamentos e estornos.
     */
    @Override
    public void updateLimit(CreditCard card) {
        log.debug("Credit card limit updated: cardId={}, currentLimit={}, totalLimit={}",
                card.getId(), card.getCurrentLimit(), card.getTotalLimit());
        creditCardRepository.save(card);
    }

    /**
     * Busca cartão ativo por id sem checar o usuário, usado por fluxos que validam propriedade fora daqui.
     */
    @Override
    public Optional<CreditCard> findById(UUID id) {
        return creditCardRepository.findByIdAndNotDeleted(id);
    }

    /**
     * Busca cartão ativo por id ou lança erro padronizado quando não existir.
     */
    @Override
    public CreditCard findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.CREDIT_CARD_NOT_FOUND));
    }

    /**
     * Busca um cartão e confirma que ele pertence ao usuário autenticado.
     */
    @Override
    public CreditCard findMyCardById(UUID id) {
        CreditCard card = findByIdOrThrow(id);
        creditCardDomainValidator.validateOwner(card, SecurityContextUtils.getCurrentUser());
        return card;
    }

    /**
     * Atualiza dados cadastrais e ajusta o limite disponível preservando o valor já utilizado.
     */
    @Override
    @Transactional
    public CreditCard updateCard(UUID id, CreditCardDTO dto) {
        CreditCard card = findMyCardById(id);
        applyLimitChange(card, dto.getTotalLimit());
        applyLinkedAccountChanges(card, dto);
        applyCardFields(card, dto);

        CreditCard updatedCard = creditCardRepository.save(card);
        log.info("Credit card updated: cardId={}", updatedCard.getId());
        return updatedCard;
    }

    /**
     * Remove logicamente o cartão e a conta espelho vinculada.
     */
    @Override
    @Transactional
    public void softDelete(UUID id) {
        CreditCard card = findMyCardById(id);
        creditCardDomainValidator.validateCanDelete(card);
        validateNoActiveFinancialLinks(card);

        long now = DateUtils.getEpochNow();
        softDeleteLinkedAccount(card, now);
        card.setDeletedAt(now);
        creditCardRepository.save(card);

        log.info("Credit card deleted: cardId={}", id);
    }

    /**
     * Monta a entidade de cartão com limite inicial completo e vínculo à conta espelho salva.
     */
    private CreditCard buildCreditCard(CreditCardDTO dto, Users user, Accounts savedAccount, long now) {
        return CreditCard.builder()
                .id(ID.generate())
                .name(dto.getName())
                .totalLimit(dto.getTotalLimit())
                .currentLimit(dto.getTotalLimit())
                .closeDay(dto.getCloseDay())
                .bestDay(dto.getBestDay())
                .user(user)
                .accounts(savedAccount)
                .enabled(true)
                .createdAt(now)
                .icon(creditCardAccountFactory.resolveIcon(dto))
                .color(creditCardAccountFactory.resolveColor(dto))
                .build();
    }

    /**
     * Recalcula o limite disponível quando o limite total do cartão é alterado.
     */
    private void applyLimitChange(CreditCard card, BigDecimal newTotalLimit) {
        if (newTotalLimit == null) {
            return;
        }
        BigDecimal usedAmount = card.getTotalLimit().subtract(card.getCurrentLimit());
        creditCardDomainValidator.validateTotalLimitCanCoverUsedAmount(newTotalLimit, usedAmount);

        BigDecimal difference = newTotalLimit.subtract(card.getTotalLimit());
        card.setTotalLimit(newTotalLimit);
        card.setCurrentLimit(card.getCurrentLimit().add(difference));
    }

    /**
     * Propaga alterações visuais e de nome para a conta espelho do cartão.
     */
    private void applyLinkedAccountChanges(CreditCard card, CreditCardDTO dto) {
        if (card.getAccounts() == null) {
            return;
        }
        creditCardAccountFactory.applyCardChangesToAccount(card.getAccounts(), dto);
        accountsRepository.save(card.getAccounts());
    }

    /**
     * Atualiza os campos cadastrais simples do cartão.
     */
    private void applyCardFields(CreditCard card, CreditCardDTO dto) {
        if (dto.getName() != null) {
            card.setName(dto.getName());
        }
        if (dto.getCloseDay() > 0) {
            card.setCloseDay(dto.getCloseDay());
        }
        if (dto.getBestDay() > 0) {
            card.setBestDay(dto.getBestDay());
        }
        if (dto.getIcon() != null) {
            card.setIcon(dto.getIcon());
        }
        if (dto.getColor() != null) {
            card.setColor(dto.getColor());
        }
        card.setUpdatedAt(DateUtils.getEpochNow());
    }

    /**
     * Marca a conta espelho como removida junto com o cartão.
     */
    private void softDeleteLinkedAccount(CreditCard card, long now) {
        if (card.getAccounts() != null) {
            card.getAccounts().setDeletedAt(now);
            accountsRepository.save(card.getAccounts());
        }
    }

    /**
     * Bloqueia exclusao quando qualquer historico financeiro ainda referencia o cartao.
     */
    private void validateNoActiveFinancialLinks(CreditCard card) {
        UUID userId = card.getUser().getId();
        UUID cardId = card.getId();
        UUID accountId = card.getAccounts() != null ? card.getAccounts().getId() : null;

        boolean hasLinkedInvoices = invoicesRepository.existsActiveByCreditCardIdAndUserId(cardId, userId);
        boolean hasLinkedInstallments = installmentPlanRepository.existsActiveByCreditCardIdAndUserId(cardId, userId);
        boolean hasDirectCardTransactions = transactionRepository.existsActiveByCreditCardIdAndUserId(cardId, userId);
        boolean hasInvoiceTransactions = transactionRepository.existsActiveByTargetInvoiceCreditCardIdAndUserId(cardId, userId);
        boolean hasMirrorAccountTransactions = accountId != null
                && transactionRepository.existsActiveByAccountIdAndUserId(accountId, userId);

        if (hasLinkedInvoices
                || hasLinkedInstallments
                || hasDirectCardTransactions
                || hasInvoiceTransactions
                || hasMirrorAccountTransactions) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CANT_DELETE_CARD_WITH_LINKS);
        }
    }
}

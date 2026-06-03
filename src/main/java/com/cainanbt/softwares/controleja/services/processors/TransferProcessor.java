package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransferProcessor implements TransactionProcessor {

    private final AccountsService accountsService;
    private final TransactionRepository repository;
    private final TransactionHelper helper;

    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return dto.getType() == TransactionType.TRANSFERENCIA;
    }

    @Override
    public Transactions process(TransactionDTO dto, Accounts accountOrigin, Category category, Users user) {
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSFER_MISSING_TARGET);
        }
        Accounts accountDest = accountsService.findByIdOrThrow(dto.getTargetAccountId());

        validateTransferableAccount(accountOrigin);
        validateTransferableAccount(accountDest);

        long dateNow = DateUtils.getEpochNow();
        RecurrenceRule rule = null;

        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            rule = helper.createRecurrenceRule(dto, TransactionType.TRANSFERENCIA, dateNow, user, accountOrigin, accountDest, category);
        }

        Transactions transferOut = createTransferObj(dto, accountOrigin, user, category, TransactionType.TRANSFERENCIA_SAIDA, dateNow, rule);
        Transactions transferIn = createTransferObj(dto, accountDest, user, category, TransactionType.TRANSFERENCIA_ENTRADA, dateNow, rule);

        transferIn.setParentTransaction(transferOut);
        repository.saveAll(List.of(transferOut, transferIn));

        if (Boolean.TRUE.equals(dto.getPaid())) {
            accountOrigin.debit(dto.getAmount());
            accountDest.credit(dto.getAmount());
            accountsService.update(accountOrigin);
            accountsService.update(accountDest);
        }

        return transferOut;
    }

    private Transactions createTransferObj(TransactionDTO dto, Accounts accounts, Users user, Category category, TransactionType transactionType, long dateNow, RecurrenceRule rule) {
        return Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(transactionType)
                .amount(dto.getAmount())
                .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                .paid(dto.getPaid())
                .enabled(true)
                .createdAt(dateNow)
                .date(dto.getDate())
                .account(accounts)
                .category(category)
                .user(user)
                .recurrenceRule(rule)
                .build();
    }

    /**
     * Garante que transferências movimentem apenas contas transacionais, sem depender da flag de saldo da dashboard.
     */
    private void validateTransferableAccount(Accounts account) {
        if (account.getType() == AccountType.CREDIT_CARD || account.getType() == AccountType.INVESTMENT) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSFER_ACCOUNT_NOT_VALID);
        }
    }
}

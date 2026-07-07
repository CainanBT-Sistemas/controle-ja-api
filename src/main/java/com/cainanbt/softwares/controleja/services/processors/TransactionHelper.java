package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TransactionHelper {
    private final AccountsService accountsService;
    private final RecurrenceRuleService recurrenceRuleService;
    private final VehicleTransactionProcessor vehicleTransactionProcessor;

    /**
     * Monta os campos comuns de uma transacao e delega dados veiculares ao processador especializado.
     */
    public Transactions.TransactionsBuilder createBaseTransactionBuilder(TransactionDTO dto, Accounts account, Category category, Users user) {
        long dateNow = DateUtils.getEpochNow();

        // BLINDAGEM: Se a descrição vier nula do app, salva como texto vazio para não quebrar o banco de dados
        String safeDescription = dto.getDescription() != null ? dto.getDescription() : "";

        var builder = Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(safeDescription)
                .type(dto.getType())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .paid(dto.getPaid())
                .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                .enabled(true)
                .account(account)
                .category(category)
                .user(user)
                .createdAt(dateNow);

        vehicleTransactionProcessor.apply(dto, builder, user);
        return builder;
    }

    /**
     * Cria a regra que gera os proximos lancamentos fixos/recorrentes.
     */
    public RecurrenceRule createRecurrenceRule(TransactionDTO dto, TransactionType transactionType, long dateNow, Users user, Accounts accountOrigin, Accounts accountDest, Category category) {
        validateMvpRecurrenceFrequency(dto.getRecurrenceFrequency());

        // BLINDAGEM: Se a descrição vier nula do app, salva como texto vazio para não quebrar o banco de dados
        String safeDescription = dto.getDescription() != null ? dto.getDescription() : "";

        RecurrenceRule rule = RecurrenceRule.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(safeDescription)
                .baseAmount(dto.getAmount())
                .type(transactionType)
                .frequency(dto.getRecurrenceFrequency())
                .startDate(dto.getDate())
                .endDate(dto.getRecurrenceEndDate())
                .status(RuleStatus.ACTIVE)
                .createdAt(dateNow)
                .user(user)
                .category(category)
                .account(accountOrigin)
                .targetAccount(accountDest)
                .build();
        return recurrenceRuleService.save(rule);
    }

    private void validateMvpRecurrenceFrequency(RecurrenceFrequency frequency) {
        if (frequency == null) {
            return;
        }
        if (frequency == RecurrenceFrequency.WEEKLY
                || frequency == RecurrenceFrequency.MONTHLY
                || frequency == RecurrenceFrequency.YEARLY) {
            return;
        }
        if (frequency == RecurrenceFrequency.BIWEEKLY) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Frequência quinzenal não está disponível neste momento.");
        }
        throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Frequência de recorrência não está disponível neste momento.");
    }

    /**
     * Aplica o efeito financeiro da transacao na conta quando ela esta paga.
     */
    public void applyAccountBalance(Transactions tx, Accounts account) {
        if (Boolean.TRUE.equals(tx.getPaid())) {
            if (tx.getType() == TransactionType.DESPESA) {
                account.debit(tx.getAmount());
            } else if (tx.getType() == TransactionType.RECEITA) {
                account.credit(tx.getAmount());
            }
            accountsService.update(account);
        }
    }

    /**
     * Calcula em qual fatura uma compra entra respeitando fechamento e melhor dia do cartao.
     */
    public LocalDateTime calculateInvoiceDate(LocalDateTime refDate, int closeDay, int bestDay) {
        int closeDayInMonth = Math.min(closeDay, refDate.toLocalDate().lengthOfMonth());
        if (refDate.getDayOfMonth() >= closeDayInMonth) {
            refDate = refDate.plusMonths(1);
        }

        int year = refDate.getYear();
        int month = refDate.getMonthValue();

        int maxDays = refDate.toLocalDate().lengthOfMonth();
        int finalDay = Math.min(bestDay, maxDays);

        LocalDateTime dueDate = LocalDateTime.of(year, month, finalDay, 23, 59, 59);
        while (dueDate.getDayOfWeek() == DayOfWeek.SATURDAY || dueDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dueDate = dueDate.plusDays(1);
        }
        return dueDate;
    }
}

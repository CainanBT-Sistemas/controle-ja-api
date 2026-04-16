package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TransactionHelper {

    private final VehicleService vehicleService;
    private final AccountsService accountsService;
    private final RecurrenceRuleService recurrenceRuleService;

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

        if (dto.getVehicleId() != null) {
            Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
            if (!vehicle.getUser().getId().equals(user.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
            }
            Double efficiency = vehicleService.processRefuel(vehicle, dto.getCurrentOdometer(), dto.getLiters(), dto.getFuelType());
            builder.vehicle(vehicle)
                    .liters(dto.getLiters())
                    .currentOdometer(dto.getCurrentOdometer())
                    .fuelType(dto.getFuelType())
                    .efficiency(efficiency);
        }
        return builder;
    }

    public RecurrenceRule createRecurrenceRule(TransactionDTO dto, TransactionType transactionType, long dateNow, Users user, Accounts accountOrigin, Accounts accountDest, Category category) {

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

    public LocalDateTime calculateInvoiceDate(LocalDateTime refDate, int closeDay, int bestDay) {
        if (refDate.getDayOfMonth() >= closeDay) {
            refDate = refDate.plusMonths(1);
        }

        int year = refDate.getYear();
        int month = refDate.getMonthValue();

        int maxDays = refDate.toLocalDate().lengthOfMonth();
        int finalDay = Math.min(bestDay, maxDays);

        return LocalDateTime.of(year, month, finalDay, 23, 59, 59);
    }
}
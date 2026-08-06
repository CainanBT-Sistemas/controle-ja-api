package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHelperTest {

    @Mock
    private AccountsService accountsService;
    @Mock
    private RecurrenceRuleService recurrenceRuleService;
    @Mock
    private VehicleTransactionProcessor vehicleTransactionProcessor;

    private TransactionHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TransactionHelper(accountsService, recurrenceRuleService, vehicleTransactionProcessor);
    }

    @Test
    void createRecurrenceRule_whenWeekly_shouldAllow() {
        TransactionDTO dto = recurrenceDto(RecurrenceFrequency.WEEKLY);
        when(recurrenceRuleService.save(any(RecurrenceRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurrenceRule rule = helper.createRecurrenceRule(
                dto,
                TransactionType.DESPESA,
                1000L,
                Users.builder().id(UUID.randomUUID()).build(),
                Accounts.builder().id(UUID.randomUUID()).build(),
                null,
                Category.builder().id(UUID.randomUUID()).build()
        );

        assertEquals(RecurrenceFrequency.WEEKLY, rule.getFrequency());
        assertEquals(RuleStatus.ACTIVE, rule.getStatus());
    }

    @Test
    void createRecurrenceRule_whenMonthly_shouldAllow() {
        TransactionDTO dto = recurrenceDto(RecurrenceFrequency.MONTHLY);
        when(recurrenceRuleService.save(any(RecurrenceRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurrenceRule rule = helper.createRecurrenceRule(
                dto,
                TransactionType.DESPESA,
                1000L,
                Users.builder().id(UUID.randomUUID()).build(),
                Accounts.builder().id(UUID.randomUUID()).build(),
                null,
                Category.builder().id(UUID.randomUUID()).build()
        );

        assertEquals(RecurrenceFrequency.MONTHLY, rule.getFrequency());
    }

    @Test
    void createRecurrenceRule_whenYearly_shouldAllow() {
        TransactionDTO dto = recurrenceDto(RecurrenceFrequency.YEARLY);
        when(recurrenceRuleService.save(any(RecurrenceRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurrenceRule rule = helper.createRecurrenceRule(
                dto,
                TransactionType.DESPESA,
                1000L,
                Users.builder().id(UUID.randomUUID()).build(),
                Accounts.builder().id(UUID.randomUUID()).build(),
                null,
                Category.builder().id(UUID.randomUUID()).build()
        );

        assertEquals(RecurrenceFrequency.YEARLY, rule.getFrequency());
    }

    @Test
    void createRecurrenceRule_whenBiweekly_shouldRejectNewRule() {
        TransactionDTO dto = recurrenceDto(RecurrenceFrequency.BIWEEKLY);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> helper.createRecurrenceRule(
                        dto,
                        TransactionType.DESPESA,
                        1000L,
                        Users.builder().id(UUID.randomUUID()).build(),
                        Accounts.builder().id(UUID.randomUUID()).build(),
                        null,
                        Category.builder().id(UUID.randomUUID()).build()
                )
        );

        assertEquals("Frequência quinzenal não está disponível neste momento.", exception.getDetail());
        verify(recurrenceRuleService, never()).save(any(RecurrenceRule.class));
    }

    @Test
    void createRecurrenceRule_whenDaily_shouldRejectNewRuleInMvp() {
        TransactionDTO dto = recurrenceDto(RecurrenceFrequency.DAILY);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> helper.createRecurrenceRule(
                        dto,
                        TransactionType.DESPESA,
                        1000L,
                        Users.builder().id(UUID.randomUUID()).build(),
                        Accounts.builder().id(UUID.randomUUID()).build(),
                        null,
                        Category.builder().id(UUID.randomUUID()).build()
                )
        );

        assertEquals("Frequência de recorrência não está disponível neste momento.", exception.getDetail());
        verify(recurrenceRuleService, never()).save(any(RecurrenceRule.class));
    }

    @Test
    void legacyBiweeklyRule_canStillBeRepresented() {
        RecurrenceRule legacyRule = RecurrenceRule.builder()
                .id(UUID.randomUUID())
                .name("Legado quinzenal")
                .baseAmount(new BigDecimal("100.00"))
                .type(TransactionType.DESPESA)
                .frequency(RecurrenceFrequency.BIWEEKLY)
                .status(RuleStatus.ACTIVE)
                .build();

        assertEquals(RecurrenceFrequency.BIWEEKLY, legacyRule.getFrequency());
    }

    @Test
    void calculateInvoiceDate_shouldReturnStartOfDueDayWithoutTimezoneD1() {
        LocalDateTime dueDate = helper.calculateInvoiceDate(
                LocalDateTime.of(2026, 8, 25, 12, 30),
                25,
                10
        );

        assertEquals(LocalDateTime.of(2026, 9, 10, 0, 0), dueDate);
        assertEquals(LocalDate.of(2026, 9, 10), DateUtils.epochToLocalDate(DateUtils.localDateTimeToEpoch(dueDate)));
    }

    @Test
    void calculateInvoiceDate_shouldReturnNinthDayWithoutTimezoneD1() {
        LocalDateTime dueDate = helper.calculateInvoiceDate(
                LocalDateTime.of(2026, 8, 25, 12, 30),
                25,
                9
        );

        assertEquals(LocalDateTime.of(2026, 9, 9, 0, 0), dueDate);
        assertEquals(LocalDate.of(2026, 9, 9), DateUtils.epochToLocalDate(DateUtils.localDateTimeToEpoch(dueDate)));
    }

    @Test
    void calculateInvoiceDate_shouldKeepWeekendBusinessDayAdjustmentWithoutExtraD1() {
        LocalDateTime dueDate = helper.calculateInvoiceDate(
                LocalDateTime.of(2026, 8, 25, 12, 30),
                25,
                12
        );

        assertEquals(LocalDateTime.of(2026, 9, 14, 0, 0), dueDate);
        assertEquals(LocalDate.of(2026, 9, 14), DateUtils.epochToLocalDate(DateUtils.localDateTimeToEpoch(dueDate)));
    }

    @Test
    void calculateInvoiceDate_shouldNotMoveLastDayOfMonthToNextMonthByTime() {
        LocalDateTime dueDate = helper.calculateInvoiceDate(
                LocalDateTime.of(2026, 8, 25, 12, 30),
                25,
                30
        );

        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0), dueDate);
        assertEquals(LocalDate.of(2026, 9, 30), DateUtils.epochToLocalDate(DateUtils.localDateTimeToEpoch(dueDate)));
    }

    private TransactionDTO recurrenceDto(RecurrenceFrequency frequency) {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Recorrencia MVP");
        dto.setDescription("Teste");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDate(1000L);
        dto.setIsFixed(true);
        dto.setRecurrenceFrequency(frequency);
        return dto;
    }
}

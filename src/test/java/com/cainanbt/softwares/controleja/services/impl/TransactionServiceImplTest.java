package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDateService;
import com.cainanbt.softwares.controleja.services.processors.TransactionHelper;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessorFactory;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleOdometerTimelineService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleRefuelMetricsService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository repository;
    @Mock
    private AccountsService accountsService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private CreditCardService creditCardService;
    @Mock
    private InvoicesService invoicesService;
    @Mock
    private InstallmentPlanService installmentPlanService;
    @Mock
    private RecurrenceRuleService recurrenceRuleService;
    @Mock
    private TransactionProcessorFactory processorFactory;
    @Mock
    private TransactionHelper helper;
    @Mock
    private GasStationRankingService gasStationRankingService;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private VehicleOdometerTimelineService odometerTimelineService;
    @Mock
    private VehicleRefuelMetricsService refuelMetricsService;
    @Mock
    private InvoiceDateService invoiceDateService;

    @InjectMocks
    private TransactionServiceImpl service;

    private Users currentUser;

    @BeforeEach
    void setUp() {
        currentUser = Users.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void updateTransactionDTO_fromThisForwardChangesOnlyCurrentAndFutureInstallments() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            Invoices openInvoice1 = invoice(false, "100.00");
            Invoices openInvoice2 = invoice(false, "100.00");
            Invoices openInvoice3 = invoice(false, "100.00");

            InstallmentPlan installment1 = installment(purchaseId, openInvoice1, 1, "100.00", false);
            InstallmentPlan installment2 = installment(purchaseId, openInvoice2, 2, "100.00", false);
            InstallmentPlan installment3 = installment(purchaseId, openInvoice3, 3, "100.00", false);

            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("300.00"))
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setAmount(new BigDecimal("120.00"));

            when(invoicesService.findById(installment2.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment2.getId())).thenReturn(Optional.of(installment2));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment1, installment2, installment3));
            when(repository.save(purchase)).thenReturn(purchase);

            service.updateTransactionDTO(installment2.getId(), dto, OperationScope.FROM_THIS_FORWARD);

            assertEquals(new BigDecimal("100.00"), installment1.getAmount());
            assertEquals(new BigDecimal("120.00"), installment2.getAmount());
            assertEquals(new BigDecimal("120.00"), installment3.getAmount());
            assertEquals(new BigDecimal("100.00"), openInvoice1.getAmount());
            assertEquals(new BigDecimal("120.00"), openInvoice2.getAmount());
            assertEquals(new BigDecimal("120.00"), openInvoice3.getAmount());
            assertEquals(new BigDecimal("340.00"), purchase.getAmount());

            ArgumentCaptor<List<InstallmentPlan>> installmentsCaptor = ArgumentCaptor.forClass(List.class);
            verify(installmentPlanService).saveAll(installmentsCaptor.capture());
            assertEquals(List.of(installment2, installment3), installmentsCaptor.getValue());

            ArgumentCaptor<List<Invoices>> invoicesCaptor = ArgumentCaptor.forClass(List.class);
            verify(invoicesService).saveAll(invoicesCaptor.capture());
            assertEquals(2, invoicesCaptor.getValue().size());
        }
    }

    @Test
    void updateTransactionDTO_fromThisForwardUpdatesRecurringPurchasesAndIgnoresUnchangedInstallmentCount() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            long selectedDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 5, 15, 0, 0));
            long futureDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 15, 0, 0));
            long newSelectedDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 5, 17, 0, 0));

            Accounts cardAccount = Accounts.builder()
                    .id(UUID.randomUUID())
                    .type(AccountType.CREDIT_CARD)
                    .user(currentUser)
                    .build();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .closeDay(25)
                    .bestDay(10)
                    .accounts(cardAccount)
                    .user(currentUser)
                    .currentLimit(new BigDecimal("500.00"))
                    .totalLimit(new BigDecimal("1000.00"))
                    .build();
            RecurrenceRule rule = RecurrenceRule.builder()
                    .id(UUID.randomUUID())
                    .name("Armazenamento")
                    .baseAmount(new BigDecimal("14.99"))
                    .type(TransactionType.DESPESA)
                    .frequency(RecurrenceFrequency.MONTHLY)
                    .startDate(selectedDate)
                    .status(RuleStatus.ACTIVE)
                    .user(currentUser)
                    .account(cardAccount)
                    .build();

            Transactions selectedPurchase = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Armazenamento")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("14.99"))
                    .date(selectedDate)
                    .account(cardAccount)
                    .creditCard(card)
                    .fixed(true)
                    .recurrenceRule(rule)
                    .user(currentUser)
                    .build();
            Transactions futurePurchase = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Armazenamento")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("14.99"))
                    .date(futureDate)
                    .account(cardAccount)
                    .creditCard(card)
                    .fixed(true)
                    .recurrenceRule(rule)
                    .user(currentUser)
                    .build();

            Invoices mayInvoice = invoice(false, "14.99", card, 5, 2026);
            Invoices juneInvoice = invoice(false, "14.99", card, 6, 2026);
            InstallmentPlan selectedInstallment = installment(selectedPurchase.getId(), mayInvoice, 1, 1, "14.99", false);
            InstallmentPlan futureInstallment = installment(futurePurchase.getId(), juneInvoice, 1, 1, "14.99", false);

            TransactionDTO dto = new TransactionDTO();
            dto.setName("Google Armazenamento");
            dto.setDate(newSelectedDate);
            dto.setInstallments(1);
            dto.setIsFixed(true);

            when(invoicesService.findById(selectedInstallment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(selectedInstallment.getId())).thenReturn(Optional.of(selectedInstallment));
            when(repository.findById(selectedPurchase.getId())).thenReturn(Optional.of(selectedPurchase));
            when(installmentPlanService.findByPurchaseId(selectedPurchase.getId())).thenReturn(List.of(selectedInstallment));
            when(installmentPlanService.findByPurchaseId(futurePurchase.getId())).thenReturn(List.of(futureInstallment));
            when(repository.findFutureUnpaidByRuleId(rule.getId(), selectedDate))
                    .thenReturn(List.of(selectedPurchase, futurePurchase));
            when(recurrenceRuleService.save(any(RecurrenceRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(helper.calculateInvoiceDate(any(LocalDateTime.class), eq(25), eq(10)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.updateTransactionDTO(selectedInstallment.getId(), dto, OperationScope.FROM_THIS_FORWARD);

            assertEquals(newSelectedDate, selectedPurchase.getDate());
            assertEquals(
                    DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 17, 0, 0)),
                    futurePurchase.getDate()
            );
            assertEquals("Google Armazenamento", selectedPurchase.getName());
            assertEquals("Google Armazenamento", futurePurchase.getName());
            assertEquals("Google Armazenamento", selectedInstallment.getName());
            assertEquals("Google Armazenamento", futureInstallment.getName());
            assertEquals(selectedPurchase.getRecurrenceRule().getId(), futurePurchase.getRecurrenceRule().getId());
            verify(repository).saveAll(List.of(selectedPurchase, futurePurchase));
            verify(installmentPlanService).saveAll(List.of(selectedInstallment, futureInstallment));
        }
    }

    @Test
    void updateTransactionDTO_whenEveryInstallmentIsPaid_doesNotPersistInstallmentOrInvoiceChanges() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            Invoices paidInvoice = invoice(true, "100.00");
            InstallmentPlan installment = installment(purchaseId, paidInvoice, 1, "100.00", true);

            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setAmount(new BigDecimal("120.00"));

            when(invoicesService.findById(installment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));

            assertThrows(BadRequestException.class, () -> service.updateTransactionDTO(installment.getId(), dto, OperationScope.ONLY_THIS));

            assertEquals(new BigDecimal("100.00"), installment.getAmount());
            assertEquals(new BigDecimal("100.00"), paidInvoice.getAmount());
            assertEquals(new BigDecimal("100.00"), purchase.getAmount());
            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenReducingInstallmentCount_recalculatesAndDeletesExceededInstallments() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("1200.00"))
                    .user(currentUser)
                    .build();

            List<InstallmentPlan> installments = java.util.stream.IntStream.rangeClosed(1, 12)
                    .mapToObj(index -> installment(purchaseId, invoice(false, "100.00"), index, 12, "100.00", false))
                    .toList();

            TransactionDTO dto = new TransactionDTO();
            dto.setInstallments(10);

            when(invoicesService.findById(installments.get(0).getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installments.get(0).getId())).thenReturn(Optional.of(installments.get(0)));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(installments);
            when(installmentPlanService.findByPurchaseIdForUpdate(purchaseId)).thenReturn(installments);
            when(repository.save(purchase)).thenReturn(purchase);

            service.updateTransactionDTO(installments.get(0).getId(), dto, OperationScope.ALL);

            for (int i = 0; i < 10; i++) {
                assertEquals(new BigDecimal("120.00"), installments.get(i).getAmount());
                assertEquals(10, installments.get(i).getTotalInstallmentsPlan());
                assertEquals(i + 1, installments.get(i).getCurrentInstallment());
                assertNull(installments.get(i).getDeletedAt());
                assertEquals(new BigDecimal("120.00"), installments.get(i).getInvoices().getAmount());
            }
            assertEquals(new BigDecimal("0.00"), installments.get(10).getInvoices().getAmount());
            assertEquals(new BigDecimal("0.00"), installments.get(11).getInvoices().getAmount());

            ArgumentCaptor<List<InstallmentPlan>> installmentsCaptor = ArgumentCaptor.forClass(List.class);
            verify(installmentPlanService).saveAll(installmentsCaptor.capture());
            assertEquals(12, installmentsCaptor.getValue().size());

            ArgumentCaptor<List<Invoices>> invoicesCaptor = ArgumentCaptor.forClass(List.class);
            verify(invoicesService).saveAll(invoicesCaptor.capture());
            assertEquals(12, invoicesCaptor.getValue().size());
        }
    }

    @Test
    void updateTransactionDTO_whenChangingInstallmentCountAndAnyInvoiceIsPaid_shouldThrow() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("1200.00"))
                    .user(currentUser)
                    .build();

            List<InstallmentPlan> installments = java.util.stream.IntStream.rangeClosed(1, 12)
                    .mapToObj(index -> installment(purchaseId, invoice(index == 5, "100.00"), index, 12, "100.00", index == 5))
                    .toList();

            TransactionDTO dto = new TransactionDTO();
            dto.setInstallments(10);

            when(invoicesService.findById(installments.get(0).getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installments.get(0).getId())).thenReturn(Optional.of(installments.get(0)));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(installments);

            assertThrows(BadRequestException.class, () -> service.updateTransactionDTO(installments.get(0).getId(), dto, OperationScope.ALL));

            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenChangingDateWithinSameInvoice_updatesOnlyPurchaseDate() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).closeDay(25).bestDay(10).build();
            Invoices invoice = invoice(false, "100.00", card, 5, 2026);
            InstallmentPlan installment = installment(purchaseId, invoice, 1, "100.00", false);
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .date(1000L)
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setDate(2000L);

            when(invoicesService.findById(installment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));
            when(helper.calculateInvoiceDate(any(LocalDateTime.class), eq(25), eq(10))).thenReturn(LocalDateTime.of(2026, 5, 10, 23, 59, 59));
            when(repository.save(purchase)).thenReturn(purchase);

            service.updateTransactionDTO(installment.getId(), dto, OperationScope.ONLY_THIS);

            assertEquals(2000L, purchase.getDate());
            verify(installmentPlanService).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenChangingDateToAnotherInvoice_movesInstallmentAndRecalculatesInvoices() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).closeDay(25).bestDay(10).build();
            Invoices invoice = invoice(false, "100.00", card, 5, 2026);
            Invoices newInvoice = invoice(false, "0.00", card, 6, 2026);
            InstallmentPlan installment = installment(purchaseId, invoice, 1, "100.00", false);
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .date(1000L)
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setDate(2000L);

            when(invoicesService.findById(installment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));
            when(helper.calculateInvoiceDate(any(LocalDateTime.class), eq(25), eq(10))).thenReturn(LocalDateTime.of(2026, 6, 10, 23, 59, 59));
            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), 6, 2026)).thenReturn(Optional.of(newInvoice));
            when(repository.save(purchase)).thenReturn(purchase);

            service.updateTransactionDTO(installment.getId(), dto, OperationScope.ONLY_THIS);

            assertEquals(BigDecimal.ZERO.setScale(2), invoice.getAmount());
            assertEquals(new BigDecimal("100.00"), newInvoice.getAmount());
            assertEquals(newInvoice, installment.getInvoices());
            verify(installmentPlanService).saveAll(anyList());
            verify(invoicesService).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenChangingDateAndAnyInvoiceIsPaid_shouldThrow() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).closeDay(25).bestDay(10).build();
            Invoices invoice = invoice(true, "100.00", card, 5, 2026);
            InstallmentPlan installment = installment(purchaseId, invoice, 1, "100.00", true);
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .date(1000L)
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setDate(2000L);

            when(invoicesService.findById(installment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));

            assertThrows(BadRequestException.class, () -> service.updateTransactionDTO(installment.getId(), dto, OperationScope.ONLY_THIS));

            assertEquals(1000L, purchase.getDate());
            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenCreditCardPurchaseAmountChanges_shouldAdjustCardLimit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            Accounts cardAccount = Accounts.builder().id(UUID.randomUUID()).type(AccountType.CREDIT_CARD).user(currentUser).build();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .currentLimit(new BigDecimal("400.00"))
                    .totalLimit(new BigDecimal("500.00"))
                    .accounts(cardAccount)
                    .build();
            Invoices openInvoice = invoice(false, "100.00", card, 5, 2026);
            InstallmentPlan installment = installment(purchaseId, openInvoice, 1, "100.00", false);
            Transactions purchase = Transactions.builder()
                    .id(purchaseId)
                    .name("Compra")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            TransactionDTO dto = new TransactionDTO();
            dto.setAmount(new BigDecimal("70.00"));

            when(invoicesService.findById(installment.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(repository.findById(purchaseId)).thenReturn(Optional.of(purchase));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));
            when(repository.save(purchase)).thenReturn(purchase);

            service.updateTransactionDTO(installment.getId(), dto, OperationScope.ONLY_THIS);

            assertEquals(new BigDecimal("70.00"), openInvoice.getAmount());
            assertEquals(new BigDecimal("70.00"), purchase.getAmount());
            assertEquals(new BigDecimal("430.00"), card.getCurrentLimit());
            verify(creditCardService).updateLimit(card);
        }
    }

    @Test
    void updateTransaction_fromThisForwardSimpleRecurringChange_updatesFutureUnpaidWithoutCreatingNewRule() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Accounts account = account();
            Category category = category();
            long juneDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 10, 0, 0));
            long julyDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 10, 0, 0));
            long augustDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 8, 10, 0, 0));
            long septemberDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 9, 10, 0, 0));
            RecurrenceRule rule = recurrenceRule(juneDate, account, category);

            Transactions past = recurringTransaction(rule, account, category, juneDate, "Aluguel", "1000.00", false);
            Transactions selected = recurringTransaction(rule, account, category, julyDate, "Aluguel", "1000.00", false);
            Transactions future = recurringTransaction(rule, account, category, augustDate, "Aluguel", "1000.00", false);
            Transactions paidFuture = recurringTransaction(rule, account, category, septemberDate, "Aluguel", "1000.00", true);

            TransactionDTO dto = new TransactionDTO();
            dto.setName("Aluguel reajustado");
            dto.setAmount(new BigDecimal("1200.00"));

            when(repository.findByIdAndNotDeleted(selected.getId())).thenReturn(Optional.of(selected));
            when(repository.findFutureUnpaidByRuleId(rule.getId(), julyDate))
                    .thenReturn(List.of(selected, future, paidFuture));
            when(recurrenceRuleService.save(rule)).thenReturn(rule);
            when(repository.save(selected)).thenReturn(selected);

            service.updateTransaction(selected.getId(), dto, OperationScope.FROM_THIS_FORWARD);

            assertEquals("Aluguel", past.getName());
            assertEquals(new BigDecimal("1000.00"), past.getAmount());
            assertEquals("Aluguel reajustado", selected.getName());
            assertEquals(new BigDecimal("1200.00"), selected.getAmount());
            assertEquals(rule.getId(), selected.getRecurrenceRule().getId());
            assertEquals("Aluguel reajustado", future.getName());
            assertEquals(new BigDecimal("1200.00"), future.getAmount());
            assertEquals(rule.getId(), future.getRecurrenceRule().getId());
            assertEquals("Aluguel", paidFuture.getName());
            assertEquals(new BigDecimal("1000.00"), paidFuture.getAmount());
            assertEquals(new BigDecimal("1200.00"), rule.getBaseAmount());

            verify(repository).saveAll(List.of(selected, future));
            verify(repository, never()).findMaxDateByRuleId(any());
        }
    }

    @Test
    void updateTransaction_fromThisForwardScheduleChange_closesOldRuleSoftDeletesFutureAndCreatesNewRule() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Accounts account = account();
            Category category = category();
            long juneDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 10, 0, 0));
            long julyDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 10, 0, 0));
            long augustDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 8, 10, 0, 0));
            long septemberDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 9, 10, 0, 0));
            RecurrenceRule oldRule = recurrenceRule(juneDate, account, category);

            Transactions selected = recurringTransaction(oldRule, account, category, julyDate, "Academia", "90.00", false);
            Transactions future = recurringTransaction(oldRule, account, category, augustDate, "Academia", "90.00", false);
            Transactions paidFuture = recurringTransaction(oldRule, account, category, septemberDate, "Academia", "90.00", true);

            TransactionDTO dto = new TransactionDTO();
            dto.setName("Academia semanal");
            dto.setAmount(new BigDecimal("50.00"));
            dto.setRecurrenceFrequency(RecurrenceFrequency.WEEKLY);

            when(repository.findByIdAndNotDeleted(selected.getId())).thenReturn(Optional.of(selected));
            when(repository.findFutureUnpaidByRuleId(oldRule.getId(), julyDate))
                    .thenReturn(List.of(selected, future, paidFuture));
            when(recurrenceRuleService.save(any(RecurrenceRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(repository.save(selected)).thenReturn(selected);
            when(repository.findMaxDateByRuleId(any())).thenReturn(
                    DateUtils.localDateTimeToEpoch(LocalDateTime.of(2027, 12, 1, 0, 0))
            );

            service.updateTransaction(selected.getId(), dto, OperationScope.FROM_THIS_FORWARD);

            assertEquals(
                    DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(julyDate).minusDays(1)),
                    oldRule.getEndDate()
            );
            assertNotNull(future.getDeletedAt());
            assertNull(paidFuture.getDeletedAt());
            assertNotNull(selected.getRecurrenceRule());
            assertNotEquals(oldRule.getId(), selected.getRecurrenceRule().getId());
            assertEquals(RecurrenceFrequency.WEEKLY, selected.getRecurrenceRule().getFrequency());
            assertEquals(julyDate, selected.getRecurrenceRule().getStartDate());
            assertFalse(Boolean.TRUE.equals(selected.getPaid()));

            verify(repository).saveAll(List.of(future));
            verify(repository).save(selected);
            verify(repository).findMaxDateByRuleId(selected.getRecurrenceRule().getId());
        }
    }

    @Test
    void softDelete_whenDeletingCreditCardInstallment_shouldRestoreCardLimitAndInvoiceAmount() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .currentLimit(new BigDecimal("300.00"))
                    .totalLimit(new BigDecimal("500.00"))
                    .build();
            Invoices openInvoice = invoice(false, "100.00", card, 5, 2026);
            InstallmentPlan installment = installment(purchaseId, openInvoice, 1, "100.00", false);

            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));

            service.softDelete(installment.getId(), OperationScope.ONLY_THIS);

            assertEquals(BigDecimal.ZERO.setScale(2), openInvoice.getAmount());
            assertEquals(new BigDecimal("400.00"), card.getCurrentLimit());
            verify(installmentPlanService).saveAll(List.of(installment));
            verify(invoicesService).saveAll(List.of(openInvoice));
            verify(creditCardService).updateLimit(card);
        }
    }

    @Test
    void updateTransactionDTO_whenOpenCashPurchaseBecomesTenInstallments_preservesTotalInvoicesAndLimit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("1500.00", 1);
            TransactionDTO dto = installmentCountDto(10);
            stubInstallmentIncrease(fixture);

            service.updateTransactionDTO(fixture.installments().get(0).getId(), dto, OperationScope.ALL);

            ArgumentCaptor<List<InstallmentPlan>> installmentCaptor = ArgumentCaptor.forClass(List.class);
            verify(installmentPlanService).saveAll(installmentCaptor.capture());
            List<InstallmentPlan> saved = installmentCaptor.getValue();

            assertEquals(10, saved.size());
            assertEquals(new BigDecimal("1500.00"), saved.stream()
                    .map(InstallmentPlan::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            assertTrue(saved.stream().allMatch(item -> item.getAmount().compareTo(new BigDecimal("150.00")) == 0));
            assertEquals(10, saved.stream().map(InstallmentPlan::getInvoices).map(Invoices::getId).distinct().count());
            assertEquals(new BigDecimal("3500.00"), fixture.card().getCurrentLimit());
            verify(creditCardService, never()).updateLimit(any(CreditCard.class));
            verify(repository).save(fixture.purchase());
            verify(repository, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenTotalHasRoundingDifference_preservesEveryCent() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("100.00", 1);
            stubInstallmentIncrease(fixture);

            service.updateTransactionDTO(
                    fixture.installments().get(0).getId(),
                    installmentCountDto(3),
                    OperationScope.ALL
            );

            ArgumentCaptor<List<InstallmentPlan>> captor = ArgumentCaptor.forClass(List.class);
            verify(installmentPlanService).saveAll(captor.capture());
            assertEquals(List.of(
                    new BigDecimal("33.34"),
                    new BigDecimal("33.33"),
                    new BigDecimal("33.33")
            ), captor.getValue().stream().map(InstallmentPlan::getAmount).toList());
            assertEquals(new BigDecimal("100.00"), captor.getValue().stream()
                    .map(InstallmentPlan::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Test
    void updateTransactionDTO_whenOpenInstallmentPurchaseIncreases_reusesPurchaseWithoutDuplicates() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("200.00", 2);
            stubInstallmentIncrease(fixture);

            service.updateTransactionDTO(
                    fixture.installments().get(0).getId(),
                    installmentCountDto(4),
                    OperationScope.ALL
            );

            ArgumentCaptor<List<InstallmentPlan>> captor = ArgumentCaptor.forClass(List.class);
            verify(installmentPlanService).saveAll(captor.capture());
            assertEquals(4, captor.getValue().size());
            assertEquals(4, captor.getValue().stream().map(InstallmentPlan::getId).distinct().count());
            assertEquals(fixture.purchase().getId(), captor.getValue().get(0).getPurchaseId());
            assertEquals(fixture.purchase().getId(), captor.getValue().get(3).getPurchaseId());
            verify(repository).save(fixture.purchase());
            verify(repository, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenInvoiceIsClosed_blocksIncreaseBeforeMutation() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("100.00", 1);
            stubInstallmentIncrease(fixture);
            when(invoiceDateService.calculateInvoiceStatus(
                    any(), any(), any(), any(), any(), any(), any()
            )).thenReturn("FECHADA");
            when(invoiceDateService.isClosedOrPaid("FECHADA")).thenReturn(true);

            BadRequestException error = assertThrows(
                    BadRequestException.class,
                    () -> service.updateTransactionDTO(
                            fixture.installments().get(0).getId(),
                            installmentCountDto(10),
                            OperationScope.ALL
                    )
            );

            assertTrue(error.getDetail().contains("fechada ou paga"));
            assertEquals(new BigDecimal("100.00"), fixture.invoices().get(0).getAmount());
            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
            verify(repository, never()).save(any(Transactions.class));
        }
    }

    @Test
    void updateTransactionDTO_whenInstallmentWasAdvanced_blocksIncrease() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("200.00", 2);
            fixture.installments().get(1).setInvoices(fixture.invoices().get(0));
            stubInstallmentIncrease(fixture);

            BadRequestException error = assertThrows(
                    BadRequestException.class,
                    () -> service.updateTransactionDTO(
                            fixture.installments().get(0).getId(),
                            installmentCountDto(4),
                            OperationScope.ALL
                    )
            );

            assertTrue(error.getDetail().contains("parcela adiantada"));
            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenInvoiceHasPaymentOrRefund_blocksIncrease() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("100.00", 1);
            stubInstallmentIncrease(fixture);
            InstallmentPlan payment = installment(
                    UUID.randomUUID(),
                    fixture.invoices().get(0),
                    1,
                    1,
                    "-25.00",
                    false
            );
            when(installmentPlanService.findByInvoiceId(fixture.invoices().get(0).getId()))
                    .thenReturn(List.of(fixture.installments().get(0), payment));

            BadRequestException error = assertThrows(
                    BadRequestException.class,
                    () -> service.updateTransactionDTO(
                            fixture.installments().get(0).getId(),
                            installmentCountDto(3),
                            OperationScope.ALL
                    )
            );

            assertTrue(error.getDetail().contains("pagamento, estorno ou adiantamento"));
            verify(installmentPlanService, never()).saveAll(anyList());
        }
    }

    @Test
    void updateTransactionDTO_whenInvoiceSaveFails_doesNotPersistPurchaseOrLimitChange() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture fixture = installmentIncreaseFixture("100.00", 1);
            stubInstallmentIncrease(fixture);
            doThrow(new IllegalStateException("database failure"))
                    .when(invoicesService).saveAll(anyList());

            assertThrows(
                    IllegalStateException.class,
                    () -> service.updateTransactionDTO(
                            fixture.installments().get(0).getId(),
                            installmentCountDto(3),
                            OperationScope.ALL
                    )
            );

            verify(repository, never()).save(any(Transactions.class));
            verify(creditCardService, never()).updateLimit(any(CreditCard.class));
            assertEquals(new BigDecimal("3500.00"), fixture.card().getCurrentLimit());
        }
    }

    @Test
    void updateTransactionDTO_whenConcurrentRetryFindsTargetCount_doesNotCreateDuplicates() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            InstallmentIncreaseFixture initial = installmentIncreaseFixture("1000.00", 1);
            InstallmentIncreaseFixture alreadyConverted = installmentIncreaseFixture("1000.00", 10);
            when(invoicesService.findById(initial.installments().get(0).getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(initial.installments().get(0).getId()))
                    .thenReturn(Optional.of(initial.installments().get(0)));
            when(repository.findById(initial.purchase().getId())).thenReturn(Optional.of(initial.purchase()));
            when(installmentPlanService.findByPurchaseId(initial.purchase().getId()))
                    .thenReturn(initial.installments());
            List<InstallmentPlan> locked = alreadyConverted.installments().stream()
                    .peek(item -> item.setPurchaseId(initial.purchase().getId()))
                    .toList();
            when(installmentPlanService.findByPurchaseIdForUpdate(initial.purchase().getId()))
                    .thenReturn(locked);

            service.updateTransactionDTO(
                    initial.installments().get(0).getId(),
                    installmentCountDto(10),
                    OperationScope.ALL
            );

            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).saveAll(anyList());
            verify(repository, never()).save(any(Transactions.class));
        }
    }

    @Test
    void softDelete_whenDeletingAdvancedInstallmentsWithDiscount_shouldReverseDiscountLimit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID purchaseId = UUID.randomUUID();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .currentLimit(new BigDecimal("320.00"))
                    .totalLimit(new BigDecimal("500.00"))
                    .build();
            Invoices invoice = invoice(false, "80.00", card, 5, 2026);
            InstallmentPlan installment = installment(purchaseId, invoice, 1, "100.00", false);
            InstallmentPlan discount = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .name("Desconto Adiantamento")
                    .amount(new BigDecimal("-20.00"))
                    .currentInstallment(1)
                    .totalInstallmentsPlan(1)
                    .paid(false)
                    .purchaseId(purchaseId)
                    .user(currentUser)
                    .invoices(invoice)
                    .build();

            when(installmentPlanService.findById(installment.getId())).thenReturn(Optional.of(installment));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment, discount));
            when(repository.findById(purchaseId)).thenReturn(Optional.empty());

            service.softDelete(installment.getId(), OperationScope.ALL);

            assertEquals(BigDecimal.ZERO.setScale(2), invoice.getAmount());
            assertEquals(new BigDecimal("400.00"), card.getCurrentLimit());
            assertNotNull(installment.getDeletedAt());
            assertNotNull(discount.getDeletedAt());
            verify(installmentPlanService).saveAll(List.of(installment, discount));
            verify(invoicesService).saveAll(List.of(invoice));
            verify(creditCardService).updateLimit(card);
        }
    }

    @Test
    void updateTransaction_whenPaidExpenseChangesAccountAndAmount_shouldReverseOldEffectAndApplyNewEffect() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Accounts oldAccount = Accounts.builder()
                    .id(UUID.randomUUID())
                    .type(AccountType.BANK)
                    .currentBalance(new BigDecimal("900.00"))
                    .user(currentUser)
                    .build();
            Accounts newAccount = Accounts.builder()
                    .id(UUID.randomUUID())
                    .type(AccountType.WALLET)
                    .currentBalance(new BigDecimal("500.00"))
                    .user(currentUser)
                    .build();
            Category category = category();
            Transactions transaction = Transactions.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Despesa paga")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("100.00"))
                    .fixed(false)
                    .paid(true)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .account(oldAccount)
                    .category(category)
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setAccountId(newAccount.getId());
            dto.setAmount(new BigDecimal("50.00"));

            when(repository.findByIdAndNotDeleted(transaction.getId())).thenReturn(Optional.of(transaction));
            when(accountsService.findByIdOrThrow(newAccount.getId())).thenReturn(newAccount);
            when(repository.save(transaction)).thenReturn(transaction);

            service.updateTransaction(transaction.getId(), dto, OperationScope.ONLY_THIS);

            assertEquals(new BigDecimal("1000.00"), oldAccount.getCurrentBalance());
            assertEquals(new BigDecimal("450.00"), newAccount.getCurrentBalance());
            assertEquals(newAccount, transaction.getAccount());
            assertEquals(new BigDecimal("50.00"), transaction.getAmount());
            verify(accountsService).update(oldAccount);
            verify(accountsService).update(newAccount);
        }
    }

    @Test
    void softDelete_whenPaidIncome_shouldReverseAccountBalanceBeforeDeleting() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Accounts account = Accounts.builder()
                    .id(UUID.randomUUID())
                    .type(AccountType.BANK)
                    .currentBalance(new BigDecimal("1200.00"))
                    .user(currentUser)
                    .build();
            Transactions transaction = Transactions.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Receita paga")
                    .type(TransactionType.RECEITA)
                    .amount(new BigDecimal("200.00"))
                    .fixed(false)
                    .paid(true)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .account(account)
                    .category(category())
                    .user(currentUser)
                    .build();

            when(installmentPlanService.findById(transaction.getId())).thenReturn(Optional.empty());
            when(repository.findByIdAndNotDeleted(transaction.getId())).thenReturn(Optional.of(transaction));

            service.softDelete(transaction.getId(), OperationScope.ONLY_THIS);

            assertEquals(new BigDecimal("1000.00"), account.getCurrentBalance());
            assertNotNull(transaction.getDeletedAt());
            verify(accountsService).update(account);
            verify(repository).save(transaction);
        }
    }

    @Test
    void updateTransaction_whenInvoicePayment_shouldBlockGenericEdit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Transactions payment = Transactions.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Pagamento Fatura")
                    .type(TransactionType.PAGAMENTO_FATURA)
                    .amount(new BigDecimal("100.00"))
                    .fixed(false)
                    .paid(true)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .account(account())
                    .category(category())
                    .user(currentUser)
                    .build();

            TransactionDTO dto = new TransactionDTO();
            dto.setAmount(new BigDecimal("90.00"));

            when(repository.findByIdAndNotDeleted(payment.getId())).thenReturn(Optional.of(payment));

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.updateTransaction(payment.getId(), dto, OperationScope.ONLY_THIS));

            assertEquals("Para corrigir pagamento de fatura, cancele o pagamento pela fatura e pague novamente.", ex.getDetail());
            verify(accountsService, never()).update(any(Accounts.class));
            verify(repository, never()).save(any(Transactions.class));
        }
    }

    @Test
    void softDelete_whenInvoicePayment_shouldBlockGenericDelete() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Transactions payment = Transactions.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Pagamento Fatura")
                    .type(TransactionType.PAGAMENTO_FATURA)
                    .amount(new BigDecimal("100.00"))
                    .fixed(false)
                    .paid(true)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .account(account())
                    .category(category())
                    .user(currentUser)
                    .build();

            when(installmentPlanService.findById(payment.getId())).thenReturn(Optional.empty());
            when(repository.findByIdAndNotDeleted(payment.getId())).thenReturn(Optional.of(payment));

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.softDelete(payment.getId(), OperationScope.ONLY_THIS));

            assertEquals("Para corrigir pagamento de fatura, cancele o pagamento pela fatura e pague novamente.", ex.getDetail());
            assertNull(payment.getDeletedAt());
            verify(accountsService, never()).update(any(Accounts.class));
            verify(repository, never()).save(any(Transactions.class));
        }
    }

    private Invoices invoice(boolean paid, String amount) {
        return invoice(paid, amount, null, null, null);
    }

    private Invoices invoice(boolean paid, String amount, CreditCard card, Integer month, Integer year) {
        return Invoices.builder()
                .id(UUID.randomUUID())
                .month(month)
                .year(year)
                .amount(new BigDecimal(amount))
                .paid(paid)
                .creditCard(card)
                .user(currentUser)
                .build();
    }

    private InstallmentPlan installment(UUID purchaseId, Invoices invoice, int currentInstallment, String amount, boolean paid) {
        return installment(purchaseId, invoice, currentInstallment, 3, amount, paid);
    }

    private InstallmentPlan installment(UUID purchaseId, Invoices invoice, int currentInstallment, int totalInstallments, String amount, boolean paid) {
        return InstallmentPlan.builder()
                .id(UUID.randomUUID())
                .purchaseId(purchaseId)
                .name("Compra (" + currentInstallment + "/" + totalInstallments + ")")
                .amount(new BigDecimal(amount))
                .type(TransactionType.DESPESA.name())
                .totalInstallmentsPlan(totalInstallments)
                .currentInstallment(currentInstallment)
                .paid(paid)
                .user(currentUser)
                .invoices(invoice)
                .build();
    }

    private Accounts account() {
        return Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.BANK)
                .user(currentUser)
                .build();
    }

    private Category category() {
        return Category.builder()
                .id(UUID.randomUUID())
                .name("Moradia")
                .categoryType(TransactionType.DESPESA.name())
                .enabled(true)
                .isSubCategory(true)
                .createdAt(DateUtils.getEpochNow())
                .user(currentUser)
                .build();
    }

    private TransactionDTO installmentCountDto(int installments) {
        TransactionDTO dto = new TransactionDTO();
        dto.setInstallments(installments);
        return dto;
    }

    private InstallmentIncreaseFixture installmentIncreaseFixture(String total, int installmentCount) {
        long purchaseDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 1, 0, 0));
        Accounts cardAccount = Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.CREDIT_CARD)
                .user(currentUser)
                .build();
        CreditCard card = CreditCard.builder()
                .id(UUID.randomUUID())
                .name("Card")
                .closeDay(25)
                .bestDay(10)
                .totalLimit(new BigDecimal("5000.00"))
                .currentLimit(new BigDecimal("3500.00"))
                .enabled(true)
                .accounts(cardAccount)
                .user(currentUser)
                .build();
        UUID purchaseId = UUID.randomUUID();
        Transactions purchase = Transactions.builder()
                .id(purchaseId)
                .name("Compra")
                .description("Descrição")
                .type(TransactionType.DESPESA)
                .amount(new BigDecimal(total))
                .date(purchaseDate)
                .fixed(false)
                .account(cardAccount)
                .creditCard(card)
                .user(currentUser)
                .build();

        BigDecimal base = new BigDecimal(total)
                .divide(BigDecimal.valueOf(installmentCount), 2, java.math.RoundingMode.DOWN);
        BigDecimal difference = new BigDecimal(total)
                .subtract(base.multiply(BigDecimal.valueOf(installmentCount)));
        List<Invoices> invoices = new java.util.ArrayList<>();
        List<InstallmentPlan> installments = new java.util.ArrayList<>();
        for (int index = 0; index < installmentCount; index++) {
            LocalDateTime invoiceDate = LocalDateTime.of(2026, 7, 10, 0, 0).plusMonths(index);
            BigDecimal amount = index == 0 ? base.add(difference) : base;
            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(invoiceDate.getMonthValue())
                    .year(invoiceDate.getYear())
                    .amount(amount)
                    .expirationDate(DateUtils.localDateTimeToEpoch(invoiceDate))
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            invoices.add(invoice);
            installments.add(InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(purchaseId)
                    .name(installmentCount > 1
                            ? "Compra (" + (index + 1) + "/" + installmentCount + ")"
                            : "Compra")
                    .description("Descrição")
                    .amount(amount)
                    .type(TransactionType.DESPESA.name())
                    .totalInstallmentsPlan(installmentCount)
                    .currentInstallment(index + 1)
                    .fixed(false)
                    .paid(false)
                    .enabled(true)
                    .date(invoice.getExpirationDate())
                    .user(currentUser)
                    .invoices(invoice)
                    .build());
        }
        return new InstallmentIncreaseFixture(purchase, card, invoices, installments);
    }

    private void stubInstallmentIncrease(InstallmentIncreaseFixture fixture) {
        InstallmentPlan reference = fixture.installments().get(0);
        when(invoicesService.findById(reference.getId())).thenReturn(Optional.empty());
        when(installmentPlanService.findById(reference.getId())).thenReturn(Optional.of(reference));
        when(repository.findById(fixture.purchase().getId())).thenReturn(Optional.of(fixture.purchase()));
        when(installmentPlanService.findByPurchaseId(fixture.purchase().getId()))
                .thenReturn(fixture.installments());
        when(installmentPlanService.findByPurchaseIdForUpdate(fixture.purchase().getId()))
                .thenReturn(fixture.installments());
        Mockito.lenient().when(repository.save(fixture.purchase())).thenReturn(fixture.purchase());
        Mockito.lenient().when(invoicesService.save(any(Invoices.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().when(invoicesService.findByCreditCardIdAndMonthAndYear(
                eq(fixture.card().getId()),
                any(Integer.class),
                any(Integer.class)
        )).thenReturn(Optional.empty());
        Mockito.lenient().when(helper.calculateInvoiceDate(
                any(LocalDateTime.class),
                eq(fixture.card().getCloseDay()),
                eq(fixture.card().getBestDay())
        )).thenAnswer(invocation -> {
            LocalDateTime source = invocation.getArgument(0);
            return LocalDateTime.of(source.getYear(), source.getMonthValue(), 10, 0, 0);
        });
        Mockito.lenient().when(invoiceDateService.calculateCloseDate(any(CreditCard.class), any(Integer.class), any(Integer.class)))
                .thenReturn(LocalDate.of(2026, 7, 25));
        Mockito.lenient().when(invoiceDateService.calculateExpirationDate(any(CreditCard.class), any(Integer.class), any(Integer.class)))
                .thenReturn(LocalDate.of(2026, 7, 10));
        Mockito.lenient().when(invoiceDateService.calculateInvoiceStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("ABERTA");
        Mockito.lenient().when(invoiceDateService.isClosedOrPaid("ABERTA")).thenReturn(false);
    }

    private record InstallmentIncreaseFixture(
            Transactions purchase,
            CreditCard card,
            List<Invoices> invoices,
            List<InstallmentPlan> installments) {
    }

    private RecurrenceRule recurrenceRule(long startDate, Accounts account, Category category) {
        return RecurrenceRule.builder()
                .id(UUID.randomUUID())
                .name("Aluguel")
                .baseAmount(new BigDecimal("1000.00"))
                .type(TransactionType.DESPESA)
                .frequency(RecurrenceFrequency.MONTHLY)
                .startDate(startDate)
                .status(RuleStatus.ACTIVE)
                .createdAt(DateUtils.getEpochNow())
                .user(currentUser)
                .account(account)
                .category(category)
                .build();
    }

    private Transactions recurringTransaction(
            RecurrenceRule rule,
            Accounts account,
            Category category,
            long date,
            String name,
            String amount,
            boolean paid
    ) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .date(date)
                .name(name)
                .type(TransactionType.DESPESA)
                .amount(new BigDecimal(amount))
                .fixed(true)
                .paid(paid)
                .enabled(true)
                .createdAt(DateUtils.getEpochNow())
                .recurrenceRule(rule)
                .account(account)
                .category(category)
                .user(currentUser)
                .build();
    }
}

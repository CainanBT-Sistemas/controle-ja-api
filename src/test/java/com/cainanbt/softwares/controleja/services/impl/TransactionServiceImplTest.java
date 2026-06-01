package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.processors.TransactionHelper;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessorFactory;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
    private VehicleLogRepository vehicleLogRepository;

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
}

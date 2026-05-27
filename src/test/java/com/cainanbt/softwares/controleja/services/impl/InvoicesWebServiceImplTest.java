package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InvoicesWebServiceImplTest {

    @Mock
    private InvoicesService invoicesService;

    @Mock
    private InstallmentPlanService installmentPlanService;

    @Mock
    private CreditCardService creditCardService;

    @Mock
    private AccountsService accountsService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private InvoicesWebServiceImpl service;

    private Users currentUser;

    @BeforeEach
    public void setUp() {
        currentUser = Users.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Test
    public void getInvoiceDetails_whenInvoiceNotFound_returnsPhantomDto() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID cardId = UUID.randomUUID();
            when(invoicesService.findByCreditCardIdAndMonthAndYear(cardId, 4, 2026)).thenReturn(Optional.empty());

            // mock card data
            CreditCard card = CreditCard.builder().id(cardId).name("MyCard").closeDay(25).bestDay(10).user(currentUser).build();
            when(creditCardService.findById(cardId)).thenReturn(Optional.of(card));

            Optional<InvoiceDetailsDTO> result = service.getInvoiceDetails(cardId, 4, 2026);
            assertTrue(result.isPresent());
            InvoiceDetailsDTO dto = result.get();
            assertNull(dto.getInvoiceId());
            assertTrue(List.of("PAGA", "ATRASADA", "FECHADA", "ABERTA", "FUTURA").contains(dto.getStatus()));
            assertEquals(cardId, dto.getCardId());
            assertEquals("MyCard", dto.getCardName());

            // closeDay 25 > bestDay 10 so closeDate should be month-1 day 25
            LocalDate expectedClose = LocalDate.of(2026, 4, 25).minusMonths(1);
            assertEquals(DateUtils.localDateToEpoch(expectedClose), dto.getCloseDate());

            // expiration should be bestDay in month (10)
            LocalDate expectedExp = LocalDate.of(2026, 4, Math.min(10, LocalDate.of(2026, 4, 1).lengthOfMonth()));
            assertEquals(DateUtils.localDateToEpoch(expectedExp), dto.getExpirationDate());
        }
    }

    @Test
    public void getInvoiceDetails_whenInvoiceFound_returnsDtoWithItemsAndStatus() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).name("Card").closeDay(5).bestDay(10).build();

            Invoices inv = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(5)
                    .year(2024)
                    .amount(new BigDecimal("100.00"))
                    .expirationDate(DateUtils.getEpochNow() + 86400000L)
                    .paid(false)
                    .creditCard(card)
                    .user(currentUser)
                    .build();

            // Adicionado o .paid(false) para blindar a regra
            InstallmentPlan p1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow()).name("A").amount(new BigDecimal("30.00")).currentInstallment(1).totalInstallmentsPlan(1).paid(false).build();
            InstallmentPlan p2 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 1000L).name("B").amount(new BigDecimal("70.00")).currentInstallment(1).totalInstallmentsPlan(1).paid(false).build();

            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), inv.getMonth(), inv.getYear())).thenReturn(Optional.of(inv));
            when(installmentPlanService.findByInvoiceId(inv.getId())).thenReturn(List.of(p1, p2));

            Optional<?> result = service.getInvoiceDetails(card.getId(), inv.getMonth(), inv.getYear());
            assertTrue(result.isPresent());
            var dto = result.get();
            assertNotNull(dto);

            assertEquals(inv.getId(), ((InvoiceDetailsDTO) dto).getInvoiceId());
            assertEquals(2, ((InvoiceDetailsDTO) dto).getItems().size());

            verify(installmentPlanService).findByInvoiceId(inv.getId());
        }
    }

    @Test
    public void processRefund_shouldCreateReversalAndUpdateInvoiceAndCard() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("100.00")).totalLimit(new BigDecimal("200.00")).build();

            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("150.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();

            InstallmentPlan original = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .name("Orig")
                    .purchaseId(UUID.randomUUID())
                    .user(currentUser)
                    .invoices(invoice)
                    .paid(false)
                    .build();

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findById(original.getId())).thenReturn(Optional.of(original));
            when(installmentPlanService.findByPurchaseId(original.getPurchaseId())).thenReturn(List.of(original));

            RefundRequestDTO req = RefundRequestDTO.builder().installmentId(original.getId()).refundAmount(new BigDecimal("20.00")).build();

            service.processRefund(invoice.getId(), req);

            ArgumentCaptor<InstallmentPlan> capt = ArgumentCaptor.forClass(InstallmentPlan.class);
            verify(installmentPlanService).save(capt.capture());
            InstallmentPlan saved = capt.getValue();

            // Garante que estorno sempre seja negativo
            assertTrue(saved.getAmount().compareTo(new BigDecimal("-20.00")) == 0 || saved.getAmount().compareTo(new BigDecimal("-20")) == 0);

            ArgumentCaptor<Invoices> invCaptor = ArgumentCaptor.forClass(Invoices.class);
            verify(invoicesService).save(invCaptor.capture());
            Invoices savedInv = invCaptor.getValue();

            // 150 + (-20) = 130
            assertEquals(new BigDecimal("130.00"), savedInv.getAmount());

            verify(creditCardService).updateLimit(any(CreditCard.class));
        }
    }

    @Test
    public void processRefund_whenInstallmentDoesNotBelongToInvoice_shouldThrow() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("150.00")).user(currentUser).build();
            Invoices otherInvoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("50.00")).user(currentUser).build();
            InstallmentPlan original = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .purchaseId(UUID.randomUUID())
                    .user(currentUser)
                    .invoices(otherInvoice)
                    .paid(false)
                    .build();

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findById(original.getId())).thenReturn(Optional.of(original));

            RefundRequestDTO req = RefundRequestDTO.builder().installmentId(original.getId()).refundAmount(new BigDecimal("20.00")).build();

            assertThrows(BadRequestException.class, () -> service.processRefund(invoice.getId(), req));
        }
    }

    @Test
    public void processRefund_whenAmountExceedsRemaining_shouldThrow() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("150.00")).user(currentUser).build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan original = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .purchaseId(purchaseId)
                    .user(currentUser)
                    .invoices(invoice)
                    .paid(false)
                    .build();

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findById(original.getId())).thenReturn(Optional.of(original));
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(original));

            RefundRequestDTO req = RefundRequestDTO.builder().installmentId(original.getId()).refundAmount(new BigDecimal("60.00")).build();

            assertThrows(BadRequestException.class, () -> service.processRefund(invoice.getId(), req));
        }
    }

    @Test
    public void advanceInstallments_shouldMoveInstallmentsAndApplyDiscount() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("50.00")).totalLimit(new BigDecimal("200.00")).build();

            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("100.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();

            Invoices oldInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("200.00")).user(currentUser).build();

            // Adicionado o .paid(false) para blindar a regra e garantir que a data do oldInvoice seja no futuro para bater com o teste
            InstallmentPlan i1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(oldInvoice).purchaseId(UUID.randomUUID()).paid(false).build();
            InstallmentPlan i2 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 200000L).amount(new BigDecimal("40.00")).invoices(oldInvoice).purchaseId(i1.getPurchaseId()).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseId(i1.getPurchaseId())).thenReturn(List.of(i1, i2));

            AdvanceRequestDTO req = AdvanceRequestDTO.builder().purchaseId(i1.getPurchaseId()).quantityToAdvance(2).discountAmount(new BigDecimal("10.00")).build();

            service.advanceInstallments(currentInvoice.getId(), req);

            verify(installmentPlanService).saveAll(anyList());
            verify(invoicesService, atLeastOnce()).save(any(Invoices.class));
            verify(creditCardService).updateLimit(any(CreditCard.class));

            // Fatura atual: 100 original + 30 (parcela 1) + 40 (parcela 2) - 10 (desconto) = 160
            assertEquals(new BigDecimal("160.00"), currentInvoice.getAmount());
        }
    }

    @Test
    public void advanceInstallments_whenQuantityExceedsAvailable_shouldThrow() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("50.00")).totalLimit(new BigDecimal("200.00")).build();
            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("100.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices futureInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("200.00")).user(currentUser).build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan i1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(futureInvoice).purchaseId(purchaseId).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(i1));

            AdvanceRequestDTO req = AdvanceRequestDTO.builder().purchaseId(purchaseId).quantityToAdvance(2).discountAmount(BigDecimal.ZERO).build();

            assertThrows(BadRequestException.class, () -> service.advanceInstallments(currentInvoice.getId(), req));
        }
    }

    @Test
    public void advanceInstallments_shouldNotDebitAccountOrCreatePaymentTransaction() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("50.00")).totalLimit(new BigDecimal("200.00")).build();
            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("100.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices oldInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("30.00")).user(currentUser).build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan installment = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(oldInvoice).purchaseId(purchaseId).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseId(purchaseId)).thenReturn(List.of(installment));

            service.advanceInstallments(currentInvoice.getId(), AdvanceRequestDTO.builder().purchaseId(purchaseId).quantityToAdvance(1).discountAmount(BigDecimal.ZERO).build());

            verify(accountsService, never()).update(any(Accounts.class));
            verify(transactionRepository, never()).save(any(Transactions.class));
            verify(transactionRepository, never()).saveAll(anyList());
            verify(creditCardService, never()).updateLimit(any(CreditCard.class));
        }
    }

    @Test
    public void processPayment_whenMissingAccountId_shouldThrowClearError() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("100.00")).user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAmount(new BigDecimal("100.00"));

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.processPayment(invoice.getId(), request));
            assertTrue(ex.getMessage().contains("Conta de pagamento não informada."));
        }
    }

    @Test
    public void processPayment_whenSourceAccountIsCreditCard_shouldThrowClearError() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("50.00", "100.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.cardAccount.getId());
            request.setAmount(new BigDecimal("100.00"));

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceId(fixture.invoice.getId())).thenReturn(List.of(purchase));
            when(accountsService.findByIdOrThrow(fixture.cardAccount.getId())).thenReturn(fixture.cardAccount);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.processPayment(fixture.invoice.getId(), request));
            assertTrue(ex.getMessage().contains("A conta de pagamento não pode ser uma conta de cartão de crédito."));
        }
    }

    @Test
    public void processPayment_whenFullPayment_shouldDebitPaidAmountAndCreateInvoiceCredit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("50.00", "100.00");
            fixture.invoice.setPaid(false);
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            Category category = Category.builder().id(UUID.randomUUID()).name("Transfêrencia").user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.sourceAccount.getId());
            request.setAmount(new BigDecimal("100.00"));
            request.setNotes("Pagamento total");
            List<InstallmentPlan> invoiceItems = new ArrayList<>();
            invoiceItems.add(purchase);

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceId(fixture.invoice.getId())).thenAnswer(invocation -> new ArrayList<>(invoiceItems));
            Mockito.doAnswer(invocation -> {
                invoiceItems.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            }).when(installmentPlanService).save(any(InstallmentPlan.class));
            when(accountsService.findByIdOrThrow(fixture.sourceAccount.getId())).thenReturn(fixture.sourceAccount);
            when(categoryService.findCategoryByUserAndName(currentUser, "Transfêrencia")).thenReturn(category);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(fixture.card.getId(), fixture.invoice.getMonth(), fixture.invoice.getYear()))
                    .thenReturn(Optional.of(fixture.invoice));

            service.processPayment(fixture.invoice.getId(), request);

            assertEquals(new BigDecimal("900.00"), fixture.sourceAccount.getCurrentBalance());
            assertEquals(new BigDecimal("100.00"), fixture.cardAccount.getCurrentBalance());
            assertEquals(new BigDecimal("600.00"), fixture.card.getCurrentLimit());
            assertEquals(new BigDecimal("0.00"), fixture.invoice.getAmount());

            ArgumentCaptor<InstallmentPlan> captor = ArgumentCaptor.forClass(InstallmentPlan.class);
            verify(installmentPlanService).save(captor.capture());
            assertEquals("Pagamento Recebido", captor.getValue().getName());
            assertEquals(new BigDecimal("-100.00"), captor.getValue().getAmount());
            verify(transactionRepository).saveAll(anyList());
        }
    }

    @Test
    public void getAdvanceablePurchases_groupAndCleanNames() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID cardId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(cardId).name("Card").closeDay(5).bestDay(10).user(currentUser).build();
            when(creditCardService.findById(cardId)).thenReturn(Optional.of(card));

            // future invoices
            Invoices inv1 = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 1000000L).paid(false).user(currentUser).build();
            Invoices inv2 = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 2000000L).paid(false).user(currentUser).build();

            when(invoicesService.findFutureUnpaidByCardAndDate(eq(currentUser.getId()), eq(cardId), anyLong())).thenReturn(List.of(inv1, inv2));

            // installments across invoices with suffixes and same purchaseId
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan p1 = InstallmentPlan.builder().id(UUID.randomUUID()).purchaseId(purchaseId).name("Compra X (1/3)").amount(new BigDecimal("100.00")).paid(false).invoices(inv1).build();
            InstallmentPlan p2 = InstallmentPlan.builder().id(UUID.randomUUID()).purchaseId(purchaseId).name("Compra X (2/3)").amount(new BigDecimal("100.00")).paid(false).invoices(inv2).build();

            when(installmentPlanService.findAdvanceableByInvoiceIds(anyList())).thenReturn(List.of(p1, p2));

            List<com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO> res = service.getAdvanceablePurchases(cardId, 4, 2026);
            assertNotNull(res);
            assertEquals(1, res.size());
            var dto = res.get(0);
            assertEquals(purchaseId, dto.getPurchaseId());
            assertEquals("Compra X", dto.getName());
            assertEquals(2, dto.getMaxInstallmentsAvailable());
        }
    }

    @Test
    public void cancelPayment_whenTotalPayment_shouldReopenAndRecalculateInvoice() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("100.00", "100.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            InstallmentPlan paymentCredit = paymentCredit(fixture.paymentOut, fixture.invoice, "100.00");

            mockCancelFlow(fixture, List.of(paymentCredit), List.of(purchase, paymentCredit));

            InvoiceDetailsDTO result = service.cancelPayment(fixture.paymentOut.getId());

            assertNotNull(result);
            assertEquals(new BigDecimal("100.00"), fixture.invoice.getAmount());
            assertEquals(false, fixture.invoice.getPaid());
            assertNotNull(fixture.paymentOut.getDeletedAt());
            assertNotNull(fixture.paymentIn.getDeletedAt());
            assertNotNull(paymentCredit.getDeletedAt());
            assertNull(purchase.getDeletedAt());
            assertEquals(new BigDecimal("100.00"), purchase.getAmount());
        }
    }

    @Test
    public void cancelPayment_whenPartialPayment_shouldRecalculateOpenAndPaidAmounts() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("50.00", "75.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "200.00", false);
            InstallmentPlan cancelledPayment = paymentCredit(fixture.paymentOut, fixture.invoice, "50.00");
            InstallmentPlan remainingPayment = paymentCredit(UUID.randomUUID(), fixture.invoice, "75.00");

            mockCancelFlow(fixture, List.of(cancelledPayment), List.of(purchase, cancelledPayment, remainingPayment));

            service.cancelPayment(fixture.paymentOut.getId());

            assertEquals(new BigDecimal("125.00"), fixture.invoice.getAmount());
            assertEquals(false, fixture.invoice.getPaid());
            assertNotNull(cancelledPayment.getDeletedAt());
            assertNull(remainingPayment.getDeletedAt());
        }
    }

    @Test
    public void cancelPayment_whenFrontendSendsInstallmentPlanId_shouldResolvePaymentTransaction() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("90.00", "0.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "90.00", false);
            InstallmentPlan paymentCredit = paymentCredit(fixture.paymentOut, fixture.invoice, "90.00");

            when(transactionRepository.findByIdIncludingDeleted(paymentCredit.getId())).thenReturn(Optional.empty());
            when(installmentPlanService.findById(paymentCredit.getId())).thenReturn(Optional.of(paymentCredit));
            mockCancelFlow(fixture, List.of(paymentCredit), List.of(purchase, paymentCredit));

            service.cancelPayment(paymentCredit.getId());

            assertNotNull(fixture.paymentOut.getDeletedAt());
            assertNotNull(fixture.paymentIn.getDeletedAt());
            assertNotNull(paymentCredit.getDeletedAt());
            assertEquals(new BigDecimal("90.00"), fixture.invoice.getAmount());
        }
    }

    @Test
    public void cancelPayment_shouldReversePaymentAccountBalance() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("80.00", "80.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "80.00", false);
            InstallmentPlan paymentCredit = paymentCredit(fixture.paymentOut, fixture.invoice, "80.00");
            mockCancelFlow(fixture, List.of(paymentCredit), List.of(purchase, paymentCredit));

            service.cancelPayment(fixture.paymentOut.getId());

            assertEquals(new BigDecimal("1080.00"), fixture.sourceAccount.getCurrentBalance());
            assertEquals(new BigDecimal("-80.00"), fixture.cardAccount.getCurrentBalance());
            assertEquals(new BigDecimal("420.00"), fixture.card.getCurrentLimit());
            verify(accountsService).update(fixture.sourceAccount);
            verify(accountsService).update(fixture.cardAccount);
            verify(creditCardService).updateLimit(fixture.card);
        }
    }

    @Test
    public void cancelPayment_whenAlreadyCancelled_shouldThrowClearError() {
        UUID paymentId = UUID.randomUUID();
        Transactions cancelledPayment = Transactions.builder()
                .id(paymentId)
                .type(TransactionType.PAGAMENTO_FATURA)
                .deletedAt(DateUtils.getEpochNow())
                .user(currentUser)
                .build();

        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            when(transactionRepository.findByIdIncludingDeleted(paymentId)).thenReturn(Optional.of(cancelledPayment));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.cancelPayment(paymentId));
            assertTrue(ex.getMessage().contains("Este pagamento já foi cancelado."));
            verify(transactionRepository, never()).save(any(Transactions.class));
        }
    }

    @Test
    public void cancelPayment_whenTransactionIsNotInvoicePayment_shouldThrow() {
        UUID transactionId = UUID.randomUUID();
        Transactions transaction = Transactions.builder()
                .id(transactionId)
                .type(TransactionType.DESPESA)
                .deletedAt(null)
                .user(currentUser)
                .build();

        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);
            when(transactionRepository.findByIdIncludingDeleted(transactionId)).thenReturn(Optional.of(transaction));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.cancelPayment(transactionId));
            assertTrue(ex.getMessage().contains("Este lançamento não é um pagamento de fatura."));
        }
    }

    @Test
    public void cancelPayment_shouldPreserveInvoicePurchases() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("100.00", "100.00");
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan purchase = installment(fixture.invoice, "Compra Parcelada", "100.00", true);
            purchase.setPurchaseId(purchaseId);
            InstallmentPlan paymentCredit = paymentCredit(fixture.paymentOut, fixture.invoice, "100.00");

            mockCancelFlow(fixture, List.of(paymentCredit), List.of(purchase, paymentCredit));

            service.cancelPayment(fixture.paymentOut.getId());

            assertEquals(purchaseId, purchase.getPurchaseId());
            assertEquals("Compra Parcelada", purchase.getName());
            assertEquals(new BigDecimal("100.00"), purchase.getAmount());
            assertEquals(true, purchase.getPaid());
            assertNull(purchase.getDeletedAt());
        }
    }

    @Test
    public void cancelPayment_whenAdvancePaymentUsesSameModel_shouldCancel() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("60.00", "0.00");
            fixture.paymentOut.setName("Adiantamento de Fatura");
            InstallmentPlan paymentCredit = paymentCredit(fixture.paymentOut, fixture.invoice, "60.00");

            mockCancelFlow(fixture, List.of(paymentCredit), List.of(paymentCredit));

            service.cancelPayment(fixture.paymentOut.getId());

            assertEquals(BigDecimal.ZERO, fixture.invoice.getAmount());
            assertNotNull(paymentCredit.getDeletedAt());
            assertNotNull(fixture.paymentOut.getDeletedAt());
        }
    }

    private void mockCancelFlow(InvoicePaymentFixture fixture, List<InstallmentPlan> paymentItems, List<InstallmentPlan> invoiceItems) {
        when(transactionRepository.findByIdIncludingDeleted(fixture.paymentOut.getId())).thenReturn(Optional.of(fixture.paymentOut));
        when(transactionRepository.findTransferChildByParentId(fixture.paymentOut.getId())).thenReturn(Optional.of(fixture.paymentIn));
        when(installmentPlanService.findByPurchaseId(fixture.paymentOut.getId())).thenReturn(paymentItems);
        when(installmentPlanService.findByInvoiceId(fixture.invoice.getId())).thenReturn(invoiceItems);
        when(invoicesService.findByCreditCardIdAndMonthAndYear(fixture.card.getId(), fixture.invoice.getMonth(), fixture.invoice.getYear()))
                .thenReturn(Optional.of(fixture.invoice));
    }

    private InvoicePaymentFixture paymentFixture(String paymentAmount, String invoiceOpenAmount) {
        Accounts sourceAccount = Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.BANK)
                .currentBalance(new BigDecimal("1000.00"))
                .user(currentUser)
                .build();
        Accounts cardAccount = Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.CREDIT_CARD)
                .currentBalance(BigDecimal.ZERO)
                .user(currentUser)
                .build();
        CreditCard card = CreditCard.builder()
                .id(UUID.randomUUID())
                .name("Card")
                .currentLimit(new BigDecimal("500.00"))
                .totalLimit(new BigDecimal("1000.00"))
                .closeDay(5)
                .bestDay(10)
                .accounts(cardAccount)
                .user(currentUser)
                .build();
        Invoices invoice = Invoices.builder()
                .id(UUID.randomUUID())
                .month(5)
                .year(2026)
                .amount(new BigDecimal(invoiceOpenAmount))
                .expirationDate(DateUtils.getEpochNow())
                .paid(true)
                .creditCard(card)
                .user(currentUser)
                .build();
        Category category = Category.builder().id(UUID.randomUUID()).name("Transfêrencia").user(currentUser).build();
        Transactions paymentOut = Transactions.builder()
                .id(UUID.randomUUID())
                .name("Pagamento Fatura Card")
                .type(TransactionType.PAGAMENTO_FATURA)
                .amount(new BigDecimal(paymentAmount))
                .paid(true)
                .account(sourceAccount)
                .category(category)
                .targetInvoice(invoice)
                .creditCard(card)
                .user(currentUser)
                .build();
        Transactions paymentIn = Transactions.builder()
                .id(UUID.randomUUID())
                .name("Recebimento de Fatura")
                .type(TransactionType.TRANSFERENCIA_ENTRADA)
                .amount(new BigDecimal(paymentAmount))
                .paid(true)
                .account(cardAccount)
                .category(category)
                .targetInvoice(invoice)
                .creditCard(card)
                .parentTransaction(paymentOut)
                .user(currentUser)
                .build();
        invoice.setTransaction(paymentOut);
        return new InvoicePaymentFixture(sourceAccount, cardAccount, card, invoice, paymentOut, paymentIn);
    }

    private InstallmentPlan installment(Invoices invoice, String name, String amount, boolean paid) {
        return InstallmentPlan.builder()
                .id(UUID.randomUUID())
                .date(DateUtils.getEpochNow())
                .name(name)
                .amount(new BigDecimal(amount))
                .currentInstallment(1)
                .totalInstallmentsPlan(1)
                .paid(paid)
                .purchaseId(UUID.randomUUID())
                .user(currentUser)
                .invoices(invoice)
                .build();
    }

    private InstallmentPlan paymentCredit(Transactions payment, Invoices invoice, String amount) {
        return paymentCredit(payment.getId(), invoice, amount);
    }

    private InstallmentPlan paymentCredit(UUID paymentId, Invoices invoice, String amount) {
        return InstallmentPlan.builder()
                .id(UUID.randomUUID())
                .date(DateUtils.getEpochNow())
                .name("Pagamento Recebido")
                .amount(new BigDecimal(amount).abs().negate())
                .currentInstallment(1)
                .totalInstallmentsPlan(1)
                .paid(true)
                .purchaseId(paymentId)
                .user(currentUser)
                .invoices(invoice)
                .build();
    }

    private record InvoicePaymentFixture(
            Accounts sourceAccount,
            Accounts cardAccount,
            CreditCard card,
            Invoices invoice,
            Transactions paymentOut,
            Transactions paymentIn
    ) {
    }
}

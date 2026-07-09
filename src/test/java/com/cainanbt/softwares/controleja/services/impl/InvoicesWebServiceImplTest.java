package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
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
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.TransactionService;
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
    private TransactionService transactionService;

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
            when(installmentPlanService.findByInvoiceIdAndUserId(inv.getId(), currentUser.getId())).thenReturn(List.of(p1, p2));

            Optional<?> result = service.getInvoiceDetails(card.getId(), inv.getMonth(), inv.getYear());
            assertTrue(result.isPresent());
            var dto = result.get();
            assertNotNull(dto);

            assertEquals(inv.getId(), ((InvoiceDetailsDTO) dto).getInvoiceId());
            assertEquals(2, ((InvoiceDetailsDTO) dto).getItems().size());

            verify(installmentPlanService).findByInvoiceIdAndUserId(inv.getId(), currentUser.getId());
        }
    }

    @Test
    public void getInvoiceDetails_ordersItemsByNewestPurchaseDateFirst() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            long invoiceDueDate = DateUtils.getEpochNow() + 86400000L;
            long olderPurchaseDate = DateUtils.getEpochNow() - 86400000L;
            long newerPurchaseDate = DateUtils.getEpochNow();
            long olderCreationDate = DateUtils.getEpochNow() - 1000L;
            long newerCreationDate = DateUtils.getEpochNow() - 2000L;

            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .name("Card")
                    .closeDay(5)
                    .bestDay(10)
                    .build();
            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(6)
                    .year(2026)
                    .amount(new BigDecimal("100.00"))
                    .expirationDate(invoiceDueDate)
                    .paid(false)
                    .creditCard(card)
                    .user(currentUser)
                    .build();

            Transactions olderPurchase = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Compra antiga")
                    .type(TransactionType.DESPESA)
                    .date(olderPurchaseDate)
                    .createdAt(olderCreationDate)
                    .build();
            Transactions newerPurchase = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Compra recente")
                    .type(TransactionType.DESPESA)
                    .date(newerPurchaseDate)
                    .createdAt(newerCreationDate)
                    .fixed(true)
                    .recurrenceRule(RecurrenceRule.builder()
                            .id(UUID.randomUUID())
                            .frequency(RecurrenceFrequency.MONTHLY)
                            .build())
                    .build();

            InstallmentPlan olderItem = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(olderPurchase.getId())
                    .date(invoiceDueDate)
                    .createdAt(olderCreationDate)
                    .name("Compra antiga")
                    .amount(new BigDecimal("40.00"))
                    .currentInstallment(1)
                    .totalInstallmentsPlan(1)
                    .paid(false)
                    .build();
            InstallmentPlan newerItem = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(newerPurchase.getId())
                    .date(invoiceDueDate)
                    .createdAt(newerCreationDate)
                    .name("Compra recente")
                    .amount(new BigDecimal("60.00"))
                    .currentInstallment(1)
                    .totalInstallmentsPlan(1)
                    .paid(false)
                    .build();

            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invoice.getMonth(), invoice.getYear()))
                    .thenReturn(Optional.of(invoice));
            when(installmentPlanService.findByInvoiceIdAndUserId(invoice.getId(), currentUser.getId()))
                    .thenReturn(List.of(olderItem, newerItem));
            when(transactionRepository.findAllById(List.of(olderPurchase.getId(), newerPurchase.getId())))
                    .thenReturn(List.of(olderPurchase, newerPurchase));

            InvoiceDetailsDTO result = service.getInvoiceDetails(card.getId(), invoice.getMonth(), invoice.getYear())
                    .orElseThrow();

            assertEquals("Compra recente", result.getItems().get(0).getName());
            assertEquals(newerPurchaseDate, result.getItems().get(0).getTransactionDate());
            assertTrue(result.getItems().get(0).getFixed());
            assertTrue(result.getItems().get(0).getIsFixed());
            assertNotNull(result.getItems().get(0).getRecurrenceRuleId());
            assertEquals(RecurrenceFrequency.MONTHLY, result.getItems().get(0).getRecurrenceFrequency());
            assertEquals("Compra antiga", result.getItems().get(1).getName());
        }
    }

    @Test
    public void getInvoiceDetails_usesInstallmentInvoiceIdAndEnrichesWithParentTransaction() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Accounts account = Accounts.builder().id(UUID.randomUUID()).name("Nubank").type(AccountType.CREDIT_CARD).user(currentUser).build();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .name("Cartao")
                    .closeDay(25)
                    .bestDay(10)
                    .accounts(account)
                    .user(currentUser)
                    .build();
            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(6)
                    .year(2026)
                    .amount(new BigDecimal("2146.87"))
                    .paid(false)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Category category = Category.builder().id(UUID.randomUUID()).name("Mercado").user(currentUser).build();
            Category invoiceCategory = Category.builder().id(UUID.randomUUID()).name("Fatura de Cartão").user(currentUser).build();
            Transactions parent = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Compra real")
                    .description("Compra parcelada")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("2146.87"))
                    .date(DateUtils.getEpochNow())
                    .paid(false)
                    .fixed(false)
                    .account(account)
                    .category(category)
                    .creditCard(card)
                    .targetInvoice(null)
                    .user(currentUser)
                    .build();
            Transactions invoiceSummaryParent = Transactions.builder()
                    .id(UUID.randomUUID())
                    .name("Fatura Cartao - Junho")
                    .type(TransactionType.DESPESA)
                    .amount(new BigDecimal("999.99"))
                    .date(DateUtils.getEpochNow())
                    .paid(false)
                    .fixed(false)
                    .account(account)
                    .category(invoiceCategory)
                    .creditCard(card)
                    .targetInvoice(null)
                    .user(currentUser)
                    .build();
            InstallmentPlan realItem = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Compra real (1/1)")
                    .description("Parcela da compra")
                    .type(TransactionType.DESPESA.name())
                    .amount(new BigDecimal("2146.87"))
                    .currentInstallment(1)
                    .totalInstallmentsPlan(1)
                    .fixed(false)
                    .paid(false)
                    .purchaseId(parent.getId())
                    .invoices(invoice)
                    .user(currentUser)
                    .build();
            InstallmentPlan invoiceSummaryItem = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow())
                    .name("Fatura Cartao - Junho")
                    .type(TransactionType.DESPESA.name())
                    .amount(new BigDecimal("999.99"))
                    .currentInstallment(1)
                    .totalInstallmentsPlan(1)
                    .fixed(false)
                    .paid(false)
                    .purchaseId(invoiceSummaryParent.getId())
                    .invoices(invoice)
                    .user(currentUser)
                    .build();

            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invoice.getMonth(), invoice.getYear())).thenReturn(Optional.of(invoice));
            when(installmentPlanService.findByInvoiceIdAndUserId(invoice.getId(), currentUser.getId())).thenReturn(List.of(realItem, invoiceSummaryItem));
            when(transactionRepository.findAllById(anyList())).thenReturn(List.of(parent, invoiceSummaryParent));

            InvoiceDetailsDTO dto = service.getInvoiceDetails(card.getId(), invoice.getMonth(), invoice.getYear()).orElseThrow();

            assertEquals(1, dto.getItems().size());
            assertEquals(realItem.getId(), dto.getItems().get(0).getId());
            assertEquals(parent.getId(), dto.getItems().get(0).getTransactionId());
            assertEquals(category.getId(), dto.getItems().get(0).getCategoryId());
            assertEquals("Mercado", dto.getItems().get(0).getCategoryName());
            assertEquals(account.getId(), dto.getItems().get(0).getAccountId());
            assertEquals(card.getId(), dto.getItems().get(0).getCreditCardId());
            assertEquals(new BigDecimal("2146.87"), dto.getTotalAmount());
        }
    }

    @Test
    public void getInvoiceDetails_whenRealInvoiceHasEightInstallments_returnsInvoiceItemsAndTotal() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID cardId = UUID.fromString("d3d5a4f4-645a-415c-8bed-a8e03843811e");
            UUID invoiceId = UUID.fromString("837ef520-07cc-4008-abdf-080732f384bb");
            Accounts account = Accounts.builder().id(UUID.randomUUID()).name("Cartao Nubank").type(AccountType.CREDIT_CARD).user(currentUser).build();
            CreditCard card = CreditCard.builder()
                    .id(cardId)
                    .name("Cartão Nubank")
                    .closeDay(25)
                    .bestDay(10)
                    .accounts(account)
                    .user(currentUser)
                    .build();
            Invoices invoice = Invoices.builder()
                    .id(invoiceId)
                    .month(8)
                    .year(2026)
                    .amount(new BigDecimal("1733.72"))
                    .paid(false)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Category category = Category.builder().id(UUID.randomUUID()).name("Compras").user(currentUser).build();

            Transactions tx1 = invoiceParentTransaction(account, card, category, "Compra 1");
            Transactions tx2 = invoiceParentTransaction(account, card, category, "Compra 2");
            Transactions tx3 = invoiceParentTransaction(account, card, category, "Compra 3");
            Transactions tx4 = invoiceParentTransaction(account, card, category, "Compra 4");
            Transactions tx5 = invoiceParentTransaction(account, card, category, "Compra 5");
            Transactions tx6 = invoiceParentTransaction(account, card, category, "Compra 6");
            Transactions tx7 = invoiceParentTransaction(account, card, category, "Compra 7");
            Transactions tx8 = invoiceParentTransaction(account, card, category, "Compra 8");

            List<InstallmentPlan> installments = List.of(
                    invoiceInstallment(invoice, tx1, "Compra 1", "200.00"),
                    invoiceInstallment(invoice, tx2, "Compra 2", "300.00"),
                    invoiceInstallment(invoice, tx3, "Compra 3", "400.00"),
                    invoiceInstallment(invoice, tx4, "Compra 4", "100.00"),
                    invoiceInstallment(invoice, tx5, "Compra 5", "250.00"),
                    invoiceInstallment(invoice, tx6, "Compra 6", "150.00"),
                    invoiceInstallment(invoice, tx7, "Compra 7", "180.00"),
                    invoiceInstallment(invoice, tx8, "Compra 8", "153.72")
            );

            when(invoicesService.findByCreditCardIdAndMonthAndYear(cardId, 8, 2026)).thenReturn(Optional.of(invoice));
            when(installmentPlanService.findByInvoiceIdAndUserId(invoiceId, currentUser.getId())).thenReturn(installments);
            when(transactionRepository.findAllById(anyList())).thenReturn(List.of(tx1, tx2, tx3, tx4, tx5, tx6, tx7, tx8));

            InvoiceDetailsDTO dto = service.getInvoiceDetails(cardId, 8, 2026).orElseThrow();

            assertEquals(invoiceId, dto.getInvoiceId());
            assertEquals(new BigDecimal("1733.72"), dto.getTotalAmount());
            assertEquals(new BigDecimal("1733.72"), dto.getOpenAmount());
            assertEquals(8, dto.getItems().size());
            verify(installmentPlanService).findByInvoiceIdAndUserId(invoiceId, currentUser.getId());
            verify(invoicesService, never()).save(any(Invoices.class));
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
            when(installmentPlanService.findByIdAndUserIdOrThrow(original.getId(), currentUser.getId())).thenReturn(original);
            when(installmentPlanService.findByPurchaseIdAndUserId(original.getPurchaseId(), currentUser.getId())).thenReturn(List.of(original));

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
            when(installmentPlanService.findByIdAndUserIdOrThrow(original.getId(), currentUser.getId())).thenReturn(original);

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
            when(installmentPlanService.findByIdAndUserIdOrThrow(original.getId(), currentUser.getId())).thenReturn(original);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(original));

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

            Invoices oldInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("200.00")).creditCard(card).user(currentUser).build();

            InstallmentPlan i1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(oldInvoice).purchaseId(UUID.randomUUID()).user(currentUser).paid(false).build();
            InstallmentPlan i2 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 200000L).amount(new BigDecimal("40.00")).invoices(oldInvoice).purchaseId(i1.getPurchaseId()).user(currentUser).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(i1.getPurchaseId(), currentUser.getId())).thenReturn(List.of(i1, i2));

            AdvanceRequestDTO req = AdvanceRequestDTO.builder().purchaseId(i1.getPurchaseId()).quantityToAdvance(2).discountAmount(new BigDecimal("10.00")).build();

            service.advanceInstallments(currentInvoice.getId(), req);

            verify(installmentPlanService, atLeastOnce()).saveAll(anyList());
            verify(invoicesService, atLeastOnce()).save(any(Invoices.class));
            verify(creditCardService).updateLimit(any(CreditCard.class));

            // Fatura atual: 100 original + 30 (parcela 1) + 40 (parcela 2) - 10 (desconto) = 160
            assertEquals(new BigDecimal("160.00"), currentInvoice.getAmount());
        }
    }

    @Test
    public void advanceInstallments_whenTwoInstallmentsHaveDiscount_shouldApplyDiscountOnce() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .currentLimit(new BigDecimal("50.00"))
                    .totalLimit(new BigDecimal("500.00"))
                    .build();
            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("100.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices futureInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow() + 500000L)
                    .amount(new BigDecimal("200.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan i1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("50.00")).invoices(futureInvoice).purchaseId(purchaseId).user(currentUser).paid(false).build();
            InstallmentPlan i2 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 200000L).amount(new BigDecimal("50.00")).invoices(futureInvoice).purchaseId(purchaseId).user(currentUser).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(i1, i2));

            service.advanceInstallments(currentInvoice.getId(), AdvanceRequestDTO.builder()
                    .purchaseId(purchaseId)
                    .quantityToAdvance(2)
                    .discountAmount(new BigDecimal("20.00"))
                    .build());

            assertEquals(new BigDecimal("180.00"), currentInvoice.getAmount());
            assertEquals(new BigDecimal("100.00"), futureInvoice.getAmount());
            assertEquals(new BigDecimal("70.00"), card.getCurrentLimit());

            ArgumentCaptor<InstallmentPlan> captor = ArgumentCaptor.forClass(InstallmentPlan.class);
            verify(installmentPlanService).save(captor.capture());
            assertEquals("Desconto Adiantamento", captor.getValue().getName());
            assertEquals(new BigDecimal("-20.00"), captor.getValue().getAmount());
            assertEquals(purchaseId, captor.getValue().getPurchaseId());
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
            Invoices futureInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("200.00")).creditCard(card).user(currentUser).build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan i1 = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(futureInvoice).purchaseId(purchaseId).user(currentUser).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(i1));

            AdvanceRequestDTO req = AdvanceRequestDTO.builder().purchaseId(purchaseId).quantityToAdvance(2).discountAmount(BigDecimal.ZERO).build();

            assertThrows(BadRequestException.class, () -> service.advanceInstallments(currentInvoice.getId(), req));
        }
    }

    @Test
    public void advanceInstallments_whenDiscountExceedsTotal_shouldThrowBeforeMutating() {
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
            Invoices futureInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow() + 500000L)
                    .amount(new BigDecimal("30.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("30.00"))
                    .invoices(futureInvoice)
                    .purchaseId(purchaseId)
                    .user(currentUser)
                    .paid(false)
                    .build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(installment));

            AdvanceRequestDTO request = AdvanceRequestDTO.builder()
                    .purchaseId(purchaseId)
                    .quantityToAdvance(1)
                    .discountAmount(new BigDecimal("31.00"))
                    .build();

            assertThrows(BadRequestException.class, () -> service.advanceInstallments(currentInvoice.getId(), request));
            assertEquals(new BigDecimal("100.00"), currentInvoice.getAmount());
            assertEquals(new BigDecimal("30.00"), futureInvoice.getAmount());
            assertEquals(futureInvoice, installment.getInvoices());
            verify(installmentPlanService, never()).save(any(InstallmentPlan.class));
            verify(installmentPlanService, never()).saveAll(anyList());
            verify(invoicesService, never()).save(any(Invoices.class));
            verify(invoicesService, never()).saveAll(anyList());
            verify(creditCardService, never()).updateLimit(any(CreditCard.class));
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
            Invoices oldInvoice = Invoices.builder().id(UUID.randomUUID()).expirationDate(DateUtils.getEpochNow() + 500000L).amount(new BigDecimal("30.00")).creditCard(card).user(currentUser).build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan installment = InstallmentPlan.builder().id(UUID.randomUUID()).date(DateUtils.getEpochNow() + 100000L).amount(new BigDecimal("30.00")).invoices(oldInvoice).purchaseId(purchaseId).user(currentUser).paid(false).build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(installment));

            service.advanceInstallments(currentInvoice.getId(), AdvanceRequestDTO.builder().purchaseId(purchaseId).quantityToAdvance(1).discountAmount(BigDecimal.ZERO).build());

            verify(accountsService, never()).update(any(Accounts.class));
            verify(transactionRepository, never()).save(any(Transactions.class));
            verify(transactionRepository, never()).saveAll(anyList());
            verify(creditCardService, never()).updateLimit(any(CreditCard.class));
        }
    }

    @Test
    public void advanceInstallments_whenSelectedInstallmentsAreLessThanInvoiceOpen_shouldSucceed() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("100.00")).totalLimit(new BigDecimal("5000.00")).build();
            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("2460.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices futureInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .expirationDate(DateUtils.getEpochNow() + 500000L)
                    .amount(new BigDecimal("500.00"))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .date(DateUtils.getEpochNow() + 100000L)
                    .amount(new BigDecimal("500.00"))
                    .invoices(futureInvoice)
                    .purchaseId(purchaseId)
                    .user(currentUser)
                    .paid(false)
                    .build();

            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(installment));

            service.advanceInstallments(currentInvoice.getId(), AdvanceRequestDTO.builder().purchaseId(purchaseId).quantityToAdvance(1).discountAmount(BigDecimal.ZERO).build());

            assertEquals(new BigDecimal("2960.00"), currentInvoice.getAmount());
            assertEquals(new BigDecimal("0.00"), futureInvoice.getAmount());
            assertEquals(currentInvoice, installment.getInvoices());
            verify(transactionRepository, never()).save(any(Transactions.class));
            verify(transactionRepository, never()).saveAll(anyList());
        }
    }

    @Test
    public void correctAdvance_whenOpenAndUnpaid_shouldReturnInstallmentsAndReverseDiscountLimit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID operationId = UUID.randomUUID();
            CreditCard card = CreditCard.builder()
                    .id(UUID.randomUUID())
                    .name("Card")
                    .currentLimit(new BigDecimal("120.00"))
                    .totalLimit(new BigDecimal("500.00"))
                    .user(currentUser)
                    .build();
            Invoices targetInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(7)
                    .year(2026)
                    .expirationDate(DateUtils.getEpochNow())
                    .amount(new BigDecimal("180.00"))
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices originalInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(8)
                    .year(2026)
                    .expirationDate(DateUtils.getEpochNow() + 500000L)
                    .amount(new BigDecimal("0.00"))
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan moved = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .date(targetInvoice.getExpirationDate())
                    .name("Compra (Adiantada)")
                    .type(TransactionType.DESPESA.name())
                    .amount(new BigDecimal("100.00"))
                    .totalInstallmentsPlan(3)
                    .currentInstallment(2)
                    .fixed(false)
                    .paid(false)
                    .purchaseId(purchaseId)
                    .advanceOperationId(operationId)
                    .advancedFromInvoice(originalInvoice)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .invoices(targetInvoice)
                    .user(currentUser)
                    .build();
            InstallmentPlan discount = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .date(targetInvoice.getExpirationDate())
                    .name("Desconto Adiantamento")
                    .type(TransactionType.RECEITA.name())
                    .amount(new BigDecimal("-20.00"))
                    .totalInstallmentsPlan(1)
                    .currentInstallment(1)
                    .fixed(false)
                    .paid(false)
                    .purchaseId(purchaseId)
                    .advanceOperationId(operationId)
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .invoices(targetInvoice)
                    .user(currentUser)
                    .build();

            when(invoicesService.findByIdOrThrow(targetInvoice.getId())).thenReturn(targetInvoice);
            when(installmentPlanService.findByAdvanceOperationIdAndUserIdForUpdate(operationId, currentUser.getId()))
                    .thenReturn(List.of(moved, discount));
            when(installmentPlanService.findByInvoiceIdAndUserId(targetInvoice.getId(), currentUser.getId()))
                    .thenReturn(List.of(moved, discount));
            when(invoicesService.findByIdOrThrow(targetInvoice.getId())).thenReturn(targetInvoice);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), targetInvoice.getMonth(), targetInvoice.getYear()))
                    .thenReturn(Optional.of(targetInvoice));
            when(installmentPlanService.findByInvoiceIdAndUserId(targetInvoice.getId(), currentUser.getId()))
                    .thenReturn(List.of(moved, discount));

            service.correctAdvance(targetInvoice.getId(), operationId);

            assertEquals(originalInvoice, moved.getInvoices());
            assertNotNull(moved.getAdvanceCorrectedAt());
            assertNotNull(discount.getDeletedAt());
            assertEquals(new BigDecimal("100.00"), originalInvoice.getAmount());
            assertEquals(new BigDecimal("100.00"), targetInvoice.getAmount());
            assertEquals(new BigDecimal("100.00"), card.getCurrentLimit());
            verify(installmentPlanService).saveAll(List.of(moved, discount));
            verify(invoicesService).saveAll(anyList());
            verify(creditCardService).updateLimit(card);
        }
    }

    @Test
    public void correctAdvance_whenInvoiceHasPayment_shouldReject() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID operationId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).user(currentUser).build();
            Invoices targetInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            Invoices originalInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            InstallmentPlan moved = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("100.00"))
                    .paid(false)
                    .purchaseId(UUID.randomUUID())
                    .advanceOperationId(operationId)
                    .advancedFromInvoice(originalInvoice)
                    .invoices(targetInvoice)
                    .user(currentUser)
                    .build();
            InstallmentPlan payment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .name("Pagamento Recebido")
                    .amount(new BigDecimal("-50.00"))
                    .paid(true)
                    .purchaseId(UUID.randomUUID())
                    .invoices(targetInvoice)
                    .user(currentUser)
                    .build();

            when(invoicesService.findByIdOrThrow(targetInvoice.getId())).thenReturn(targetInvoice);
            when(installmentPlanService.findByAdvanceOperationIdAndUserIdForUpdate(operationId, currentUser.getId()))
                    .thenReturn(List.of(moved));
            when(installmentPlanService.findByInvoiceIdAndUserId(targetInvoice.getId(), currentUser.getId()))
                    .thenReturn(List.of(moved, payment));

            assertThrows(BadRequestException.class, () -> service.correctAdvance(targetInvoice.getId(), operationId));
            verify(installmentPlanService, never()).saveAll(anyList());
        }
    }

    @Test
    public void updateInvoiceItem_whenPendingAdvance_shouldRejectGenericEdit() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .paid(false)
                    .enabled(true)
                    .user(currentUser)
                    .build();
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(UUID.randomUUID())
                    .advanceOperationId(UUID.randomUUID())
                    .paid(false)
                    .invoices(invoice)
                    .user(currentUser)
                    .build();

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findByIdAndUserIdOrThrow(installment.getId(), currentUser.getId())).thenReturn(installment);

            assertThrows(BadRequestException.class, () ->
                    service.updateInvoiceItem(invoice.getId(), installment.getId(), new TransactionDTO(), OperationScope.ONLY_THIS));
            verify(transactionService, never()).updateTransactionDTO(any(), any(), any());
        }
    }

    @Test
    public void updateInvoiceItem_whenValid_delegatesToTransactionServiceAndReturnsUpdatedInvoice() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).name("Card").user(currentUser).build();
            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(6)
                    .year(2026)
                    .paid(false)
                    .enabled(true)
                    .user(currentUser)
                    .creditCard(card)
                    .build();
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(UUID.randomUUID())
                    .paid(false)
                    .invoices(invoice)
                    .user(currentUser)
                    .build();
            TransactionDTO request = new TransactionDTO();
            request.setName("Compra ajustada");
            request.setType(TransactionType.DESPESA);
            request.setAmount(new BigDecimal("120.00"));
            request.setDate(DateUtils.getEpochNow());
            request.setPaid(false);
            request.setIsFixed(false);

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findByIdAndUserIdOrThrow(installment.getId(), currentUser.getId())).thenReturn(installment);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invoice.getMonth(), invoice.getYear()))
                    .thenReturn(Optional.of(invoice));

            InvoiceDetailsDTO result = service.updateInvoiceItem(invoice.getId(), installment.getId(), request, OperationScope.FROM_THIS_FORWARD);

            assertEquals(invoice.getId(), result.getInvoiceId());
            verify(transactionService).updateTransactionDTO(installment.getId(), request, OperationScope.FROM_THIS_FORWARD);
        }
    }

    @Test
    public void cancelPurchase_whenValid_delegatesDeleteAllAndReturnsUpdatedInvoice() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            CreditCard card = CreditCard.builder().id(UUID.randomUUID()).name("Card").user(currentUser).build();
            Invoices invoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(6)
                    .year(2026)
                    .paid(false)
                    .enabled(true)
                    .user(currentUser)
                    .creditCard(card)
                    .build();
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(purchaseId)
                    .currentInstallment(1)
                    .paid(false)
                    .invoices(invoice)
                    .user(currentUser)
                    .build();

            when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
            when(installmentPlanService.findActiveByPurchaseIdAndUserId(purchaseId, currentUser.getId())).thenReturn(List.of(installment));
            when(invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invoice.getMonth(), invoice.getYear()))
                    .thenReturn(Optional.of(invoice));

            InvoiceDetailsDTO result = service.cancelPurchase(invoice.getId(), purchaseId);

            assertEquals(invoice.getId(), result.getInvoiceId());
            verify(transactionService).softDelete(installment.getId(), OperationScope.ALL);
        }
    }

    @Test
    public void processPayment_whenMissingAccountId_shouldThrowClearError() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("100.00")).user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAmount(new BigDecimal("100.00"));

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
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenReturn(List.of(purchase));
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
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenAnswer(invocation -> new ArrayList<>(invoiceItems));
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
    public void processPayment_whenPartialPayment_shouldDebitPaidAmountAndKeepInvoiceOpen() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("40.00", "100.00");
            fixture.invoice.setPaid(false);
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            Category category = Category.builder().id(UUID.randomUUID()).name("Transfêrencia").user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.sourceAccount.getId());
            request.setAmount(new BigDecimal("40.00"));
            request.setNotes("Pagamento parcial");
            List<InstallmentPlan> invoiceItems = new ArrayList<>();
            invoiceItems.add(purchase);

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenAnswer(invocation -> new ArrayList<>(invoiceItems));
            Mockito.doAnswer(invocation -> {
                invoiceItems.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            }).when(installmentPlanService).save(any(InstallmentPlan.class));
            when(accountsService.findByIdOrThrow(fixture.sourceAccount.getId())).thenReturn(fixture.sourceAccount);
            when(categoryService.findCategoryByUserAndName(currentUser, "Transfêrencia")).thenReturn(category);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(fixture.card.getId(), fixture.invoice.getMonth(), fixture.invoice.getYear()))
                    .thenReturn(Optional.of(fixture.invoice));

            service.processPayment(fixture.invoice.getId(), request);

            assertEquals(new BigDecimal("960.00"), fixture.sourceAccount.getCurrentBalance());
            assertEquals(new BigDecimal("40.00"), fixture.cardAccount.getCurrentBalance());
            assertEquals(new BigDecimal("540.00"), fixture.card.getCurrentLimit());
            assertEquals(new BigDecimal("60.00"), fixture.invoice.getAmount());
            assertEquals(false, fixture.invoice.getPaid());

            ArgumentCaptor<InstallmentPlan> captor = ArgumentCaptor.forClass(InstallmentPlan.class);
            verify(installmentPlanService).save(captor.capture());
            assertEquals("Pagamento Recebido", captor.getValue().getName());
            assertEquals(new BigDecimal("-40.00"), captor.getValue().getAmount());
            verify(transactionRepository).saveAll(anyList());
            verify(installmentPlanService, never()).saveAll(anyList());
        }
    }

    @Test
    public void processPayment_whenMultiplePartialPayments_shouldSumPaymentsAndKeepRemainingOpen() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("40.00", "70.00");
            fixture.invoice.setPaid(false);
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            InstallmentPlan previousPayment = paymentCredit(UUID.randomUUID(), fixture.invoice, "30.00");
            Category category = Category.builder().id(UUID.randomUUID()).name("Transfêrencia").user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.sourceAccount.getId());
            request.setAmount(new BigDecimal("40.00"));
            List<InstallmentPlan> invoiceItems = new ArrayList<>();
            invoiceItems.add(purchase);
            invoiceItems.add(previousPayment);

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenAnswer(invocation -> new ArrayList<>(invoiceItems));
            Mockito.doAnswer(invocation -> {
                invoiceItems.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            }).when(installmentPlanService).save(any(InstallmentPlan.class));
            when(accountsService.findByIdOrThrow(fixture.sourceAccount.getId())).thenReturn(fixture.sourceAccount);
            when(categoryService.findCategoryByUserAndName(currentUser, "Transfêrencia")).thenReturn(category);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(fixture.card.getId(), fixture.invoice.getMonth(), fixture.invoice.getYear()))
                    .thenReturn(Optional.of(fixture.invoice));

            service.processPayment(fixture.invoice.getId(), request);

            assertEquals(new BigDecimal("30.00"), fixture.invoice.getAmount());
            assertEquals(false, fixture.invoice.getPaid());
        }
    }

    @Test
    public void processPayment_whenPartialThenFinalPayment_shouldCloseInvoice() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("60.00", "60.00");
            fixture.invoice.setPaid(false);
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            InstallmentPlan previousPayment = paymentCredit(UUID.randomUUID(), fixture.invoice, "40.00");
            Category category = Category.builder().id(UUID.randomUUID()).name("Transfêrencia").user(currentUser).build();
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.sourceAccount.getId());
            request.setAmount(new BigDecimal("60.00"));
            List<InstallmentPlan> invoiceItems = new ArrayList<>();
            invoiceItems.add(purchase);
            invoiceItems.add(previousPayment);

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenAnswer(invocation -> new ArrayList<>(invoiceItems));
            Mockito.doAnswer(invocation -> {
                invoiceItems.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            }).when(installmentPlanService).save(any(InstallmentPlan.class));
            when(accountsService.findByIdOrThrow(fixture.sourceAccount.getId())).thenReturn(fixture.sourceAccount);
            when(categoryService.findCategoryByUserAndName(currentUser, "Transfêrencia")).thenReturn(category);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(fixture.card.getId(), fixture.invoice.getMonth(), fixture.invoice.getYear()))
                    .thenReturn(Optional.of(fixture.invoice));

            service.processPayment(fixture.invoice.getId(), request);

            assertEquals(new BigDecimal("0.00"), fixture.invoice.getAmount());
            assertEquals(true, fixture.invoice.getPaid());
            assertEquals(true, purchase.getPaid());
            assertEquals(true, previousPayment.getPaid());
            verify(installmentPlanService).saveAll(anyList());
        }
    }

    @Test
    public void processPayment_whenAmountAboveOpenAmount_shouldThrowBeforeMutating() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("101.00", "100.00");
            InstallmentPlan purchase = installment(fixture.invoice, "Compra", "100.00", false);
            InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
            request.setAccountId(fixture.sourceAccount.getId());
            request.setAmount(new BigDecimal("101.00"));

            when(invoicesService.findByIdOrThrow(fixture.invoice.getId())).thenReturn(fixture.invoice);
            when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenReturn(List.of(purchase));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.processPayment(fixture.invoice.getId(), request));

            assertTrue(ex.getMessage().contains("O pagamento não pode ser maior que o saldo em aberto."));
            assertEquals(new BigDecimal("1000.00"), fixture.sourceAccount.getCurrentBalance());
            assertEquals(BigDecimal.ZERO, fixture.cardAccount.getCurrentBalance());
            assertEquals(new BigDecimal("500.00"), fixture.card.getCurrentLimit());
            verify(accountsService, never()).findByIdOrThrow(any(UUID.class));
            verify(transactionRepository, never()).saveAll(anyList());
            verify(installmentPlanService, never()).save(any(InstallmentPlan.class));
            verify(invoicesService, never()).save(any(Invoices.class));
        }
    }

    @Test
    public void processPayment_whenZeroOrNegativeAmount_shouldThrowBeforeMutating() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            InvoicePaymentFixture fixture = paymentFixture("0.00", "100.00");
            InvoicePaymentRequestDTO zeroRequest = new InvoicePaymentRequestDTO();
            zeroRequest.setAccountId(fixture.sourceAccount.getId());
            zeroRequest.setAmount(BigDecimal.ZERO);
            InvoicePaymentRequestDTO negativeRequest = new InvoicePaymentRequestDTO();
            negativeRequest.setAccountId(fixture.sourceAccount.getId());
            negativeRequest.setAmount(new BigDecimal("-1.00"));

            BadRequestException zero = assertThrows(BadRequestException.class, () -> service.processPayment(fixture.invoice.getId(), zeroRequest));
            BadRequestException negative = assertThrows(BadRequestException.class, () -> service.processPayment(fixture.invoice.getId(), negativeRequest));

            assertTrue(zero.getMessage().contains("O valor do pagamento deve ser maior que zero."));
            assertTrue(negative.getMessage().contains("O valor do pagamento deve ser maior que zero."));
            verify(invoicesService, never()).findByIdOrThrow(any(UUID.class));
            verify(transactionRepository, never()).saveAll(anyList());
        }
    }

    @Test
    public void getAdvanceablePurchases_groupAndCleanNames() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID cardId = UUID.randomUUID();
            CreditCard card = CreditCard.builder().id(cardId).name("Card").closeDay(5).bestDay(10).user(currentUser).build();
            when(creditCardService.findById(cardId)).thenReturn(Optional.of(card));

            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(4)
                    .year(2026)
                    .expirationDate(DateUtils.localDateToEpoch(LocalDate.of(2026, 4, 10)))
                    .creditCard(card)
                    .user(currentUser)
                    .build();
            when(invoicesService.findByCreditCardIdAndMonthAndYear(cardId, 4, 2026))
                    .thenReturn(Optional.of(currentInvoice));

            // future invoices
            Invoices inv1 = Invoices.builder().id(UUID.randomUUID()).expirationDate(currentInvoice.getExpirationDate() + 1000000L).paid(false).enabled(true).creditCard(card).user(currentUser).build();
            Invoices inv2 = Invoices.builder().id(UUID.randomUUID()).expirationDate(currentInvoice.getExpirationDate() + 2000000L).paid(false).enabled(true).creditCard(card).user(currentUser).build();

            when(invoicesService.findFutureUnpaidByCardAndDate(currentUser.getId(), cardId, currentInvoice.getExpirationDate())).thenReturn(List.of(inv1, inv2));

            // installments across invoices with suffixes and same purchaseId
            UUID purchaseId = UUID.randomUUID();
            InstallmentPlan p1 = InstallmentPlan.builder().id(UUID.randomUUID()).purchaseId(purchaseId).name("Compra X (1/3)").amount(new BigDecimal("100.00")).paid(false).enabled(true).user(currentUser).invoices(inv1).build();
            InstallmentPlan p2 = InstallmentPlan.builder().id(UUID.randomUUID()).purchaseId(purchaseId).name("Compra X (2/3)").amount(new BigDecimal("100.00")).paid(false).enabled(true).user(currentUser).invoices(inv2).build();

            when(installmentPlanService.findAdvanceableByInvoiceIdsAndUserId(anyList(), eq(currentUser.getId()))).thenReturn(List.of(p1, p2));

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
    public void getAdvanceablePurchases_afterAdvancingFourOfTen_shouldReturnOnlyRemainingFutureInstallments() {
        try (MockedStatic<SecurityContextUtils> mocked = Mockito.mockStatic(SecurityContextUtils.class)) {
            mocked.when(SecurityContextUtils::getCurrentUser).thenReturn(currentUser);

            UUID cardId = UUID.randomUUID();
            UUID purchaseId = UUID.randomUUID();
            long currentExpiration = DateUtils.localDateToEpoch(LocalDate.of(2026, 4, 10));
            CreditCard card = CreditCard.builder()
                    .id(cardId)
                    .name("Card")
                    .currentLimit(new BigDecimal("1000.00"))
                    .totalLimit(new BigDecimal("5000.00"))
                    .user(currentUser)
                    .build();
            Invoices currentInvoice = Invoices.builder()
                    .id(UUID.randomUUID())
                    .month(4)
                    .year(2026)
                    .expirationDate(currentExpiration)
                    .amount(new BigDecimal("100.00"))
                    .paid(false)
                    .enabled(true)
                    .creditCard(card)
                    .user(currentUser)
                    .build();

            List<Invoices> futureInvoices = new ArrayList<>();
            List<InstallmentPlan> purchaseInstallments = new ArrayList<>();
            for (int installmentNumber = 2; installmentNumber <= 10; installmentNumber++) {
                Invoices futureInvoice = Invoices.builder()
                        .id(UUID.randomUUID())
                        .month(installmentNumber + 2)
                        .year(2026)
                        .expirationDate(currentExpiration + installmentNumber * 1000000L)
                        .amount(new BigDecimal("100.00"))
                        .paid(false)
                        .enabled(true)
                        .creditCard(card)
                        .user(currentUser)
                        .build();
                futureInvoices.add(futureInvoice);
                purchaseInstallments.add(InstallmentPlan.builder()
                        .id(UUID.randomUUID())
                        .purchaseId(purchaseId)
                        .name("Compra 10x (" + installmentNumber + "/10)")
                        .amount(new BigDecimal("100.00"))
                        .paid(false)
                        .enabled(true)
                        .user(currentUser)
                        .invoices(futureInvoice)
                        .build());
            }

            when(creditCardService.findById(cardId)).thenReturn(Optional.of(card));
            when(invoicesService.findByIdOrThrow(currentInvoice.getId())).thenReturn(currentInvoice);
            when(invoicesService.findByCreditCardIdAndMonthAndYear(cardId, 4, 2026))
                    .thenReturn(Optional.of(currentInvoice));
            when(installmentPlanService.findByPurchaseIdAndUserId(purchaseId, currentUser.getId()))
                    .thenReturn(purchaseInstallments);

            service.advanceInstallments(currentInvoice.getId(), AdvanceRequestDTO.builder()
                    .purchaseId(purchaseId)
                    .quantityToAdvance(4)
                    .discountAmount(BigDecimal.ZERO)
                    .build());

            Invoices lastFutureInvoice = futureInvoices.get(futureInvoices.size() - 1);
            purchaseInstallments.add(InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(purchaseId)
                    .name("Parcela paga")
                    .amount(new BigDecimal("100.00"))
                    .paid(true)
                    .enabled(true)
                    .user(currentUser)
                    .invoices(lastFutureInvoice)
                    .build());
            purchaseInstallments.add(InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(purchaseId)
                    .name("Parcela removida")
                    .amount(new BigDecimal("100.00"))
                    .paid(false)
                    .enabled(true)
                    .deletedAt(DateUtils.getEpochNow())
                    .user(currentUser)
                    .invoices(lastFutureInvoice)
                    .build());
            purchaseInstallments.add(InstallmentPlan.builder()
                    .id(UUID.randomUUID())
                    .purchaseId(purchaseId)
                    .name("Parcela desabilitada")
                    .amount(new BigDecimal("100.00"))
                    .paid(false)
                    .enabled(false)
                    .user(currentUser)
                    .invoices(lastFutureInvoice)
                    .build());

            List<Invoices> remainingFutureInvoices = futureInvoices.stream()
                    .filter(invoice -> invoice.getExpirationDate() > currentInvoice.getExpirationDate())
                    .filter(invoice -> invoice.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            when(invoicesService.findFutureUnpaidByCardAndDate(
                    currentUser.getId(),
                    cardId,
                    currentInvoice.getExpirationDate()
            )).thenReturn(remainingFutureInvoices);
            when(installmentPlanService.findAdvanceableByInvoiceIdsAndUserId(anyList(), eq(currentUser.getId())))
                    .thenReturn(purchaseInstallments);

            List<com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO> result =
                    service.getAdvanceablePurchases(cardId, 4, 2026);

            assertEquals(1, result.size());
            assertEquals(5, result.get(0).getMaxInstallmentsAvailable());
            assertEquals(new BigDecimal("500.00"), result.get(0).getEstimatedAmount());
            assertEquals(5, result.get(0).getInstallmentAmounts().size());
            assertTrue(purchaseInstallments.subList(0, 4).stream()
                    .allMatch(item -> item.getInvoices().getId().equals(currentInvoice.getId())));
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
        when(installmentPlanService.findByPurchaseIdAndUserId(fixture.paymentOut.getId(), currentUser.getId())).thenReturn(paymentItems);
        when(installmentPlanService.findByInvoiceIdAndUserId(fixture.invoice.getId(), currentUser.getId())).thenReturn(invoiceItems);
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

    private Transactions invoiceParentTransaction(Accounts account, CreditCard card, Category category, String name) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(name + " descrição")
                .type(TransactionType.DESPESA)
                .amount(BigDecimal.ONE)
                .date(DateUtils.getEpochNow())
                .paid(false)
                .fixed(false)
                .account(account)
                .category(category)
                .creditCard(card)
                .targetInvoice(null)
                .user(currentUser)
                .build();
    }

    private InstallmentPlan invoiceInstallment(Invoices invoice, Transactions parent, String name, String amount) {
        return InstallmentPlan.builder()
                .id(UUID.randomUUID())
                .date(DateUtils.getEpochNow())
                .name(name)
                .description(name + " parcela")
                .type(TransactionType.DESPESA.name())
                .amount(new BigDecimal(amount))
                .currentInstallment(1)
                .totalInstallmentsPlan(1)
                .fixed(false)
                .paid(false)
                .purchaseId(parent.getId())
                .enabled(true)
                .createdAt(DateUtils.getEpochNow())
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


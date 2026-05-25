package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
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
}

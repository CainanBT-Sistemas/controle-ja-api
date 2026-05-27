package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
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
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InvoicePaymentProcessorTest {

    @Mock
    private AccountsService accountsService;

    @Mock
    private CreditCardService creditCardService;

    @Mock
    private InvoicesService invoicesService;

    @Mock
    private InstallmentPlanService installmentPlanService;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private InvoicePaymentProcessor processor;

    private Accounts sourceAccount;
    private Accounts cardAccount;
    private Users user;

    @BeforeEach
    public void setUp() {
        user = Users.builder().id(UUID.randomUUID()).build();
        sourceAccount = Accounts.builder().id(UUID.randomUUID()).type(AccountType.WALLET).name("Source").currency("BRL").currentBalance(new BigDecimal("5000")).initialBalance(new BigDecimal("5000")).calculateBalance(true).enabled(true).user(user).createdAt(DateUtils.getEpochNow()).build();
        cardAccount = Accounts.builder().id(UUID.randomUUID()).type(AccountType.CREDIT_CARD).name("CardAcc").currency("BRL").currentBalance(new BigDecimal("0")).initialBalance(new BigDecimal("0")).calculateBalance(false).enabled(true).user(user).createdAt(DateUtils.getEpochNow()).build();
    }

    @Test
    public void process_whenPaymentIsPartial_shouldAddCreditAndKeepInvoiceOpen() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pay");
        dto.setDescription("Partial");
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setAccountId(sourceAccount.getId());
        dto.setTargetAccountId(cardAccount.getId());
        dto.setPaid(true);

        CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("500.00")).totalLimit(new BigDecimal("2000.00")).accounts(cardAccount).build();
        Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("2000.00")).paid(false).creditCard(card).user(user).build();
        dto.setTargetInvoiceId(invoice.getId());

        when(accountsService.findById(cardAccount.getId())).thenReturn(Optional.of(cardAccount));
        when(creditCardService.findByAccountId(cardAccount.getId())).thenReturn(card);
        when(invoicesService.findByIdOrThrow(any())).thenReturn(invoice);

        Transactions out = processor.process(dto, sourceAccount, null, user);

        // Verify payment credit saved
        ArgumentCaptor<InstallmentPlan> ipCap = ArgumentCaptor.forClass(InstallmentPlan.class);
        verify(installmentPlanService).save(ipCap.capture());
        InstallmentPlan saved = ipCap.getValue();
        assertEquals(new BigDecimal("-1000.00"), saved.getAmount());
        assertEquals("Pagamento Recebido", saved.getName());

        // Invoice amount reduced
        ArgumentCaptor<Invoices> invCap = ArgumentCaptor.forClass(Invoices.class);
        verify(invoicesService).save(invCap.capture());
        Invoices savedInv = invCap.getValue();
        assertEquals(new BigDecimal("1000.00"), savedInv.getAmount());
        assertFalse(savedInv.getPaid());

        // Card limit restored
        verify(creditCardService).updateLimit(any(CreditCard.class));
    }

    @Test
    public void process_whenPaymentIsTotal_shouldMarkInvoiceAsPaid() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pay");
        dto.setDescription("Full");
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setAccountId(sourceAccount.getId());
        dto.setTargetAccountId(cardAccount.getId());
        dto.setPaid(true);

        CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("500.00")).totalLimit(new BigDecimal("2000.00")).accounts(cardAccount).build();
        Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("1000.00")).paid(false).creditCard(card).user(user).build();
        dto.setTargetInvoiceId(invoice.getId());

        InstallmentPlan inst1 = InstallmentPlan.builder().id(UUID.randomUUID()).amount(new BigDecimal("500.00")).paid(false).invoices(invoice).build();
        InstallmentPlan inst2 = InstallmentPlan.builder().id(UUID.randomUUID()).amount(new BigDecimal("500.00")).paid(false).invoices(invoice).build();

        when(accountsService.findById(cardAccount.getId())).thenReturn(Optional.of(cardAccount));
        when(creditCardService.findByAccountId(cardAccount.getId())).thenReturn(card);
        when(invoicesService.findByIdOrThrow(any())).thenReturn(invoice);
        when(installmentPlanService.findByInvoiceId(invoice.getId())).thenReturn(List.of(inst1, inst2));

        Transactions out = processor.process(dto, sourceAccount, null, user);

        // Verify payment credit saved
        ArgumentCaptor<InstallmentPlan> ipCap = ArgumentCaptor.forClass(InstallmentPlan.class);
        verify(installmentPlanService).save(ipCap.capture());
        InstallmentPlan saved = ipCap.getValue();
        assertEquals(new BigDecimal("-1000.00"), saved.getAmount());

        // Invoice zeroed and marked paid
        ArgumentCaptor<Invoices> invCap = ArgumentCaptor.forClass(Invoices.class);
        verify(invoicesService).save(invCap.capture());
        Invoices savedInv = invCap.getValue();
        assertEquals(BigDecimal.ZERO, savedInv.getAmount());
        assertTrue(savedInv.getPaid());

        // All installments marked paid
        verify(installmentPlanService).saveAll(argThat(list -> list.stream().allMatch(p -> p.getPaid())));
    }

    @Test
    public void process_whenPaymentIsGreaterThanTotal_shouldThrow() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pay");
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setAmount(new BigDecimal("1200.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setTargetAccountId(cardAccount.getId());
        dto.setPaid(true);

        CreditCard card = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("500.00")).totalLimit(new BigDecimal("2000.00")).accounts(cardAccount).build();
        Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("1000.00")).paid(false).creditCard(card).user(user).build();
        dto.setTargetInvoiceId(invoice.getId());

        when(accountsService.findById(cardAccount.getId())).thenReturn(Optional.of(cardAccount));
        when(creditCardService.findByAccountId(cardAccount.getId())).thenReturn(card);
        when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);
        assertThrows(BadRequestException.class, () -> processor.process(dto, sourceAccount, null, user));
    }

    @Test
    public void process_whenTargetAccountDoesNotBelongToInvoiceCard_shouldThrow() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pay");
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setTargetAccountId(cardAccount.getId());
        dto.setPaid(true);

        Accounts otherCardAccount = Accounts.builder().id(UUID.randomUUID()).type(AccountType.CREDIT_CARD).name("Other").currency("BRL").currentBalance(BigDecimal.ZERO).initialBalance(BigDecimal.ZERO).calculateBalance(false).enabled(true).user(user).createdAt(DateUtils.getEpochNow()).build();
        CreditCard paymentCard = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("500.00")).totalLimit(new BigDecimal("2000.00")).accounts(cardAccount).build();
        CreditCard invoiceCard = CreditCard.builder().id(UUID.randomUUID()).currentLimit(new BigDecimal("500.00")).totalLimit(new BigDecimal("2000.00")).accounts(otherCardAccount).build();
        Invoices invoice = Invoices.builder().id(UUID.randomUUID()).amount(new BigDecimal("100.00")).paid(false).creditCard(invoiceCard).user(user).build();
        dto.setTargetInvoiceId(invoice.getId());

        when(accountsService.findById(cardAccount.getId())).thenReturn(Optional.of(cardAccount));
        when(creditCardService.findByAccountId(cardAccount.getId())).thenReturn(paymentCard);
        when(invoicesService.findByIdOrThrow(invoice.getId())).thenReturn(invoice);

        assertThrows(BadRequestException.class, () -> processor.process(dto, sourceAccount, null, user));
    }

    @Test
    public void process_whenSourceAccountIsCreditCard_shouldThrow() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pay");
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setTargetAccountId(cardAccount.getId());
        dto.setPaid(true);

        assertThrows(BadRequestException.class, () -> processor.process(dto, cardAccount, null, user));
    }
}

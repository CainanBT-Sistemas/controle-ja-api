package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardExpenseProcessorTest {

    @Mock
    private CreditCardService creditCardService;
    @Mock
    private TransactionRepository repository;
    @Mock
    private InvoicesService invoicesService;
    @Mock
    private InstallmentPlanService installmentPlanService;
    @Mock
    private AccountsService accountsService;
    @Mock
    private TransactionHelper helper;

    @InjectMocks
    private CreditCardExpenseProcessor processor;

    private Users user;
    private Accounts cardAccount;
    private Category category;

    @BeforeEach
    void setUp() {
        user = Users.builder().id(UUID.randomUUID()).build();
        cardAccount = Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.CREDIT_CARD)
                .name("Cartao")
                .institution("Banco")
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(false)
                .enabled(true)
                .user(user)
                .createdAt(DateUtils.getEpochNow())
                .build();
        category = Category.builder().id(UUID.randomUUID()).name("Compras").user(user).build();
    }

    @Test
    void process_whenCreditCardExpense_shouldConsumeLimitAndCreateInvoiceWithoutDebitingAccountBalance() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Compra no cartão");
        dto.setDescription("Mercado");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("120.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(false);
        dto.setInstallments(1);

        CreditCard card = CreditCard.builder()
                .id(UUID.randomUUID())
                .name("Card")
                .currentLimit(new BigDecimal("500.00"))
                .totalLimit(new BigDecimal("500.00"))
                .closeDay(25)
                .bestDay(10)
                .accounts(cardAccount)
                .user(user)
                .build();
        Invoices invoice = Invoices.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.ZERO)
                .paid(false)
                .creditCard(card)
                .user(user)
                .build();
        Transactions purchase = Transactions.builder()
                .id(UUID.randomUUID())
                .name(dto.getName())
                .type(TransactionType.DESPESA)
                .amount(dto.getAmount())
                .account(cardAccount)
                .category(category)
                .user(user)
                .build();

        when(creditCardService.findByAccountId(cardAccount.getId())).thenReturn(card);
        when(helper.createBaseTransactionBuilder(dto, cardAccount, category, user)).thenReturn(Transactions.builder()
                .id(purchase.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .paid(dto.getPaid())
                .fixed(false)
                .enabled(true)
                .account(cardAccount)
                .category(category)
                .user(user)
                .createdAt(DateUtils.getEpochNow()));
        when(repository.save(any(Transactions.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(helper.calculateInvoiceDate(any(), anyInt(), anyInt())).thenReturn(DateUtils.epochToLocalDateTime(dto.getDate()));
        when(invoicesService.findByCreditCardIdAndMonthAndYear(any(), any(), any())).thenReturn(Optional.of(invoice));

        processor.process(dto, cardAccount, category, user);

        assertEquals(BigDecimal.ZERO, cardAccount.getCurrentBalance());
        assertEquals(new BigDecimal("380.00"), card.getCurrentLimit());
        assertEquals(new BigDecimal("120.00"), invoice.getAmount());
        verify(accountsService, never()).update(cardAccount);

        ArgumentCaptor<Transactions> transactionCaptor = ArgumentCaptor.forClass(Transactions.class);
        verify(repository).save(transactionCaptor.capture());
        assertEquals(false, transactionCaptor.getValue().getPaid());
    }
}

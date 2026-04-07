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
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;
    private final VehicleService vehicleService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final RecurrenceRuleService recurrenceRuleService;

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        Accounts account = accountsService.findById(dto.getAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }

        Category category = categoryService.findById(dto.getCategoryId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));

        if (dto.getType() == TransactionType.TRANSFERENCIA) {
            return processTransfer(dto, account, category, user);
        }

        if (dto.getType() == TransactionType.PAGAMENTO_FATURA) {
            return processInvoicePayment(dto, account, category, user);
        }

        if (account.getType() == AccountType.CREDIT_CARD) {
            return processCreditCardExpense(dto, account, category, user);
        }

        return processNormalTransaction(dto, account, category, user);
    }

    @Override
    public List<Transactions> listLastTransactions(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start != null && end != null) {
            return repository.findTransactionsByMonth(user.getId(), start, end);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<Transactions> findById(UUID id) {
        return repository.findByIdAndNotDeleted(id);
    }

    @Override
    public Transactions findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSACTION_NOT_FOUND));
    }

    @Override
    public Transactions updateTransaction(UUID id, TransactionDTO dto) {
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();
        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (dto.getName() != null) transaction.setName(dto.getName());
        if (dto.getDescription() != null) transaction.setDescription(dto.getDescription());
        if (dto.getType() != null) transaction.setType(dto.getType());
        if (dto.getAmount() != null) transaction.setAmount(dto.getAmount());
        if (dto.getDate() != null) transaction.setDate(dto.getDate());
        if (dto.getPaid() != null) transaction.setPaid(dto.getPaid());

        if (dto.getAccountId() != null) {
            Accounts account = accountsService.findById(dto.getAccountId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));
            if (!account.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
            }
            transaction.setAccount(account);
        }
        if (dto.getCategoryId() != null) {
            Category category = categoryService.findById(dto.getCategoryId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));
            transaction.setCategory(category);
        }

        transaction.setUpdatedAt(dateNow);

        return repository.save(transaction);
    }

    @Override
    public void softDelete(UUID id) {
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();
        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (transaction.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        transaction.setDeletedAt(dateNow);
        repository.save(transaction);
    }


    //PRIVATES

    private Transactions processTransfer(TransactionDTO dto, Accounts accountOrigin, Category category, Users user) {
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSFER_MISSING_TARGET);
        }
        Accounts accountDest = accountsService.findByIdOrThrow(dto.getTargetAccountId());

        if (accountDest.getType() != AccountType.BANK && accountDest.getType() != AccountType.WALLET) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSFER_TARGET_NOT_VALID_ACCOUNT);
        }

        long dateNow = DateUtils.getEpochNow();

        RecurrenceRule rule = null;

        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            rule = createRecurrenceRule(dto, TransactionType.TRANSFERENCIA, dateNow, user, accountOrigin, accountDest, category);
        }

        Transactions transferOut = createTransfer(dto, accountOrigin, user, category, TransactionType.TRANSFERENCIA_SAIDA, dateNow, rule);
        Transactions transferIn = createTransfer(dto, accountDest, user, category, TransactionType.TRANSFERENCIA_ENTRADA, dateNow, rule);

        transferIn.setParentTransaction(transferOut);

        repository.saveAll(List.of(transferOut, transferIn));

        if (Boolean.TRUE.equals(dto.getPaid())) {
            accountOrigin.debit(dto.getAmount());
            accountDest.credit(dto.getAmount());
            accountsService.update(accountOrigin);
            accountsService.update(accountDest);
        }

        return transferOut;
    }

    private RecurrenceRule createRecurrenceRule(TransactionDTO dto, TransactionType transactionType, long dateNow, Users user, Accounts accountOrigin, Accounts accountDest, Category category) {
        RecurrenceRule rule = RecurrenceRule.builder()
                .id(ID.generate())
                .description(dto.getName())
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

    private Transactions createTransfer(TransactionDTO dto, Accounts accounts, Users user, Category category, TransactionType transactionType, long dateNow, RecurrenceRule rule) {
        return Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(transactionType)
                .amount(dto.getAmount())
                .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                .paid(dto.getPaid())
                .enabled(true)
                .createdAt(dateNow)
                .date(dto.getDate())
                .account(accounts)
                .category(category)
                .user(user)
                .recurrenceRule(rule)
                .build();
    }

    private Transactions processInvoicePayment(TransactionDTO dto, Accounts sourceAccount, Category category, Users user) {
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_MISSING_TARGET);
        }
        Accounts cardAccount = accountsService.findById(dto.getTargetAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ACCOUNT_NOT_FOUND));

        if (cardAccount.getType() != AccountType.CREDIT_CARD) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_TARGET_NOT_CARD);
        }

        long dateNow = DateUtils.getEpochNow();

        Transactions paymentOut = Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(TransactionType.PAGAMENTO_FATURA)
                .amount(dto.getAmount())
                .fixed(false)
                .paid(dto.getPaid())
                .enabled(true)
                .createdAt(dateNow)
                .date(dto.getDate())
                .account(sourceAccount)
                .category(category)
                .user(user)
                .build();

        Transactions paymentIn = Transactions.builder()
                .id(ID.generate())
                .name("Recebimento de Fatura")
                .description(dto.getDescription())
                .type(TransactionType.TRANSFERENCIA_ENTRADA) // Representa a entrada do dinheiro para zerar a fatura
                .amount(dto.getAmount())
                .fixed(false)
                .paid(dto.getPaid())
                .enabled(true)
                .createdAt(dateNow)
                .date(dto.getDate())
                .account(cardAccount)
                .category(category)
                .user(user)
                .parentTransaction(paymentOut)
                .build();

        repository.saveAll(List.of(paymentOut, paymentIn));

        if (Boolean.TRUE.equals(dto.getPaid())) {
            //MOVIMENTAÇÃO DE SALDO
            sourceAccount.debit(dto.getAmount());
            accountsService.update(sourceAccount);
            cardAccount.credit(dto.getAmount());
            accountsService.update(cardAccount);
            // RESTAURA LIMITE DO CARTAO
            CreditCard card = creditCardService.findByAccountId(cardAccount.getId());
            card.restoreLimit(dto.getAmount());
            creditCardService.updateLimit(card);

            //DAR BAIXA NA FATURA E PARCELAS
            if (dto.getTargetInvoiceId() != null) {
                Invoices invoiceToPay = invoicesService.findByIdOrThrow(dto.getTargetInvoiceId());
                invoiceToPay.setPaid(true);
                invoiceToPay.setTransaction(paymentOut);
                invoicesService.save(invoiceToPay);

                List<InstallmentPlan> installmentPlans = installmentPlanService.findByInvoiceId(invoiceToPay.getId());
                installmentPlans.forEach(inst -> inst.setPaid(true));
                installmentPlanService.saveAll(installmentPlans);
            }
        }
        return paymentOut;
    }

    private Transactions processNormalTransaction(TransactionDTO dto, Accounts account, Category category, Users user) {
        Transactions.TransactionsBuilder baseTransactionBuilder = createBaseTransactionBuilder(dto, account, category, user);

        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            RecurrenceRule rule = createRecurrenceRule(dto, dto.getType(), DateUtils.getEpochNow(), user, account, null, category);
            baseTransactionBuilder.recurrenceRule(rule);
        }

        Transactions transactions = baseTransactionBuilder.build();

        if (Boolean.TRUE.equals(transactions.getPaid())) {
            if (transactions.getType() == TransactionType.DESPESA) {
                account.debit(transactions.getAmount());
            } else if (transactions.getType() == TransactionType.RECEITA) {
                account.credit(transactions.getAmount());
            }
            accountsService.update(account);
        }
        return repository.save(transactions);
    }

    private Transactions processCreditCardExpense(TransactionDTO dto, Accounts account, Category category, Users user) {
        if (dto.getType() != TransactionType.DESPESA) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ONLY_EXPENSE);
        }

        long dateNow = DateUtils.getEpochNow();

        CreditCard card = creditCardService.findByAccountId(account.getId());

        card.consumeLimit(dto.getAmount());
        creditCardService.updateLimit(card);

        Transactions purchaseTransaction = createBaseTransactionBuilder(dto, account, category, user).paid(false).build();
        purchaseTransaction = repository.save(purchaseTransaction);

        int parcelas = (dto.getInstallments() == null || dto.getInstallments() < 1) ? 1 : dto.getInstallments();

        BigDecimal valorParcela = dto.getAmount().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal diferenca = dto.getAmount().subtract(valorParcela.multiply(BigDecimal.valueOf(parcelas)));

        LocalDateTime dataCompra = DateUtils.epochToLocalDateTime(dto.getDate());

        for (int i = 0; i < parcelas; i++) {
            BigDecimal valorDestaParcela = (i == 0) ? valorParcela.add(diferenca) : valorParcela;
            //Calcula o Mês/Ano exato desta parcela baseando-se no dia de fechamento
            LocalDateTime dataVencimentoFatura = calculateInvoiceDate(dataCompra.plusMonths(i), card.getCloseDay(), card.getBestDay());
            int invMonth = dataVencimentoFatura.getMonthValue();
            int invYear = dataVencimentoFatura.getYear();

            //Busca a Fatura deste mês. Se não existir, CRIA a fatura em tempo real.
            Invoices invoice = invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invMonth, invYear)
                    .orElseGet(() -> invoicesService.save(Invoices.builder()
                            .id(ID.generate())
                            .month(invMonth)
                            .year(invYear)
                            .amount(BigDecimal.ZERO)
                            .expirationDate(DateUtils.localDateTimeToEpoch(dataVencimentoFatura))
                            .paid(false)
                            .enabled(true)
                            .createdAt(dateNow)
                            .creditCard(card)
                            .user(user)
                            .build()));
            invoice.setAmount(invoice.getAmount().add(valorDestaParcela));
            invoicesService.save(invoice);
            //Salva a Parcela (A Filha)
            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(ID.generate())
                    .name(dto.getName() + (parcelas > 1 ? " (" + (i + 1) + "/" + parcelas + ")" : ""))
                    .type(TransactionType.DESPESA.name())
                    .amount(valorDestaParcela)
                    .totalInstallmentsPlan(parcelas)
                    .currentInstallment(i + 1)
                    .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                    .paid(false)
                    .purchaseId(purchaseTransaction.getId())
                    .enabled(true)
                    .createdAt(dateNow)
                    .date(invoice.getExpirationDate())
                    .invoices(invoice)
                    .user(user)
                    .build();

            installmentPlanService.save(installment);
        }

        //O Saldo da Conta "Espelho" do cartão fica negativo para representar a dívida total
        account.debit(dto.getAmount());
        accountsService.update(account);

        return purchaseTransaction;
    }

    private Transactions.TransactionsBuilder createBaseTransactionBuilder(TransactionDTO dto, Accounts account, Category category, Users user) {
        long dateNow = DateUtils.getEpochNow();
        var builder = Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
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

    // Helper para definir Fechamento/Vencimento
    private LocalDateTime calculateInvoiceDate(LocalDateTime refDate, int closeDay, int bestDay) {
        // Se a compra ocorreu NO DIA ou DEPOIS do fechamento da fatura, ela cai pro mês seguinte
        if (refDate.getDayOfMonth() >= closeDay) {
            refDate = refDate.plusMonths(1);
        }

        int year = refDate.getYear();
        int month = refDate.getMonthValue();

        // Proteção: E se o "bestDay" for 31 e o mês for Fevereiro? Pegamos o último dia válido.
        int maxDays = refDate.toLocalDate().lengthOfMonth();
        int finalDay = Math.min(bestDay, maxDays);

        return LocalDateTime.of(year, month, finalDay, 23, 59, 59);
    }
}
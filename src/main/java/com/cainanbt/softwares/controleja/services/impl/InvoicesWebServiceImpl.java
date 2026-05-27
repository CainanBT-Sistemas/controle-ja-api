package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceItemDTO;
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
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.web.InvoicesWebService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoicesWebServiceImpl implements InvoicesWebService {

    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final CreditCardService creditCardService;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;

    @Override
    public Optional<InvoiceDetailsDTO> getInvoiceDetails(UUID cardId, Integer month, Integer year) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        Optional<Invoices> invOpt = invoicesService.findByCreditCardIdAndMonthAndYear(cardId, month, year);

        // If invoice not found, return a phantom DTO with calculated dates and computed status.
        if (invOpt.isEmpty()) {
            // Fetch card to build phantom values
            CreditCard card = creditCardService.findById(cardId).orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado."));
            if (!card.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException("Acesso Negado", "Cartão não pertence ao usuário autenticado.");
            }

            LocalDate closeLocal = calculateCloseDate(card, month, year);
            LocalDate expLocal = calculateExpirationDate(card, month, year);
            long closeEpoch = DateUtils.localDateToEpoch(closeLocal);
            long expEpoch = DateUtils.localDateToEpoch(expLocal);

            InvoiceDetailsDTO phantom = InvoiceDetailsDTO.builder()
                    .invoiceId(null)
                    .cardId(card.getId())
                    .cardName(card.getName())
                    .month(month)
                    .year(year)
                    .totalAmount(BigDecimal.ZERO)
                    .paidAmount(BigDecimal.ZERO)
                    .openAmount(BigDecimal.ZERO)
                    .expirationDate(expEpoch)
                    .closeDate(closeEpoch)
                    .status(calculateInvoiceStatus(card, false, BigDecimal.ZERO, BigDecimal.ZERO, closeLocal, expLocal, month, year))
                    .canPay(false)
                    .canAdvancePayment(false)
                    .canAdvanceInstallments(false)
                    .canRefund(false)
                    .canEditTransactions(false)
                    .canEditCard(true)
                    .items(List.of())
                    .build();

            return Optional.of(phantom);
        }

        Invoices inv = invOpt.get();

        if (!inv.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Fatura não pertence ao usuário autenticado.");
        }

        List<InstallmentPlan> items = installmentPlanService.findByInvoiceId(inv.getId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .sorted(Comparator.comparing(InstallmentPlan::getDate))
                .collect(Collectors.toList());

        InvoiceTotals totals = calculateTotals(items);
        if (syncInvoiceOpenAmount(inv, totals.openAmount())) {
            invoicesService.save(inv);
        }

        LocalDate closeLocal = calculateCloseDate(inv.getCreditCard(), inv.getMonth(), inv.getYear());
        LocalDate expirationLocal = calculateExpirationDate(inv.getCreditCard(), inv.getMonth(), inv.getYear());
        Long closeDate = DateUtils.localDateToEpoch(closeLocal);
        Long expirationDate = DateUtils.localDateToEpoch(expirationLocal);
        String status = calculateInvoiceStatus(inv.getCreditCard(), inv.getPaid(), totals.totalAmount(), totals.openAmount(), closeLocal, expirationLocal, inv.getMonth(), inv.getYear());
        boolean closedOrPaid = isClosedOrPaid(status);

        List<InvoiceItemDTO> itemDTOs = items.stream().map(i -> toInvoiceItemDTO(i, closedOrPaid)).collect(Collectors.toList());

        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder()
                .invoiceId(inv.getId())
                .cardId(inv.getCreditCard() != null ? inv.getCreditCard().getId() : null)
                .cardName(inv.getCreditCard() != null ? inv.getCreditCard().getName() : null)
                .month(inv.getMonth())
                .year(inv.getYear())
                .totalAmount(totals.totalAmount())
                .paidAmount(totals.paidAmount())
                .openAmount(totals.openAmount())
                .expirationDate(expirationDate)
                .closeDate(closeDate)
                .status(status)
                .canPay(totals.openAmount().compareTo(BigDecimal.ZERO) > 0)
                .canAdvancePayment(totals.openAmount().compareTo(BigDecimal.ZERO) > 0)
                .canAdvanceInstallments(!closedOrPaid)
                .canRefund(items.stream().anyMatch(this::isRefundableItem))
                .canEditTransactions(!closedOrPaid)
                .canEditCard(true)
                .items(itemDTOs)
                .build();

        return Optional.of(dto);
    }

    private LocalDate calculateCloseDate(CreditCard card, Integer month, Integer year) {
        LocalDate closeDate = safeDate(year, month, card.getCloseDay());
        if (card.getCloseDay() > card.getBestDay()) {
            closeDate = closeDate.minusMonths(1);
        }
        return nextBusinessDay(closeDate);
    }

    private LocalDate calculateExpirationDate(CreditCard card, Integer month, Integer year) {
        return nextBusinessDay(safeDate(year, month, card.getBestDay()));
    }

    private LocalDate safeDate(Integer year, Integer month, int requestedDay) {
        try {
            int monthLength = LocalDate.of(year, month, 1).lengthOfMonth();
            return LocalDate.of(year, month, Math.min(requestedDay, monthLength));
        } catch (DateTimeException e) {
            return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    private LocalDate nextBusinessDay(LocalDate date) {
        LocalDate adjusted = date;
        while (isWeekend(adjusted) || isHoliday(adjusted)) {
            adjusted = adjusted.plusDays(1);
        }
        return adjusted;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private boolean isHoliday(LocalDate date) {
        // Preparado para feriados: hoje não há calendário configurado, então nenhum feriado é aplicado.
        return false;
    }

    private String calculateInvoiceStatus(CreditCard card, Boolean paid, BigDecimal totalAmount, BigDecimal openAmount, LocalDate closeDate, LocalDate expirationDate, Integer month, Integer year) {
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        LocalDate previousCloseDate = calculatePreviousCloseDate(card, month, year);

        if (!today.isBefore(previousCloseDate) && today.isBefore(closeDate)) {
            return "ABERTA";
        }

        if (today.isAfter(expirationDate)) {
            if (Boolean.TRUE.equals(paid) || openAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return "PAGA";
            }
            return "ATRASADA";
        }

        if (!today.isBefore(closeDate) && !today.isAfter(expirationDate)) {
            if (Boolean.TRUE.equals(paid) || openAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return "PAGA";
            }
            return "FECHADA";
        }

        return "FUTURA";
    }

    private LocalDate calculatePreviousCloseDate(CreditCard card, Integer month, Integer year) {
        LocalDate invoiceMonth = LocalDate.of(year, month, 1).minusMonths(1);
        return calculateCloseDate(card, invoiceMonth.getMonthValue(), invoiceMonth.getYear());
    }

    private boolean isClosedOrPaid(String status) {
        return "PAGA".equals(status) || "ATRASADA".equals(status) || "FECHADA".equals(status);
    }

    private InvoiceItemDTO toInvoiceItemDTO(InstallmentPlan item, boolean closedOrPaid) {
        String itemKind = resolveItemKind(item);
        return InvoiceItemDTO.builder()
                .id(item.getId())
                .transactionId(item.getPurchaseId())
                .purchaseId(item.getPurchaseId())
                .date(item.getDate())
                .name(item.getName())
                .categoryName(resolveCategoryName(item))
                .currentInstallment(item.getCurrentInstallment())
                .totalInstallmentsPlan(item.getTotalInstallmentsPlan())
                .type(item.getType())
                .amount(item.getAmount())
                .canEdit(!closedOrPaid && !Boolean.TRUE.equals(item.getPaid()) && "PURCHASE".equals(itemKind))
                .itemKind(itemKind)
                .build();
    }

    private String resolveCategoryName(InstallmentPlan item) {
        if (item.getPurchaseId() == null || transactionRepository == null) return null;
        return transactionRepository.findById(item.getPurchaseId())
                .map(Transactions::getCategory)
                .map(Category::getName)
                .orElse(null);
    }

    private String resolveItemKind(InstallmentPlan item) {
        String name = item.getName() != null ? item.getName() : "";
        if (isPaymentItem(item)) return "PAYMENT";
        if (name.startsWith("Estorno:")) return "REFUND";
        if (name.contains("(Adiantada)") || "Desconto Adiantamento".equals(name)) return "INSTALLMENT_ADVANCED";
        if (item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) < 0) return "ADJUSTMENT";
        return "PURCHASE";
    }

    private boolean isPaymentItem(InstallmentPlan item) {
        String name = item.getName() != null ? item.getName() : "";
        return name.startsWith("Pagamento Recebido");
    }

    private boolean isRefundableItem(InstallmentPlan item) {
        return item.getDeletedAt() == null
                && item.getAmount() != null
                && item.getAmount().compareTo(BigDecimal.ZERO) > 0
                && !Boolean.TRUE.equals(item.getPaid());
    }

    private InvoiceTotals calculateTotals(List<InstallmentPlan> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;

        for (InstallmentPlan item : items) {
            if (item.getAmount() == null) continue;
            if (isPaymentItem(item)) {
                paidAmount = paidAmount.add(item.getAmount().abs());
            } else {
                totalAmount = totalAmount.add(item.getAmount());
            }
        }

        BigDecimal openAmount = totalAmount.subtract(paidAmount);
        if (openAmount.compareTo(BigDecimal.ZERO) < 0) {
            openAmount = BigDecimal.ZERO;
        }
        return new InvoiceTotals(totalAmount, paidAmount, openAmount);
    }

    private boolean syncInvoiceOpenAmount(Invoices invoice, BigDecimal openAmount) {
        boolean changed = false;
        if (invoice.getAmount() == null || invoice.getAmount().compareTo(openAmount) != 0) {
            invoice.setAmount(openAmount);
            changed = true;
        }
        if (openAmount.compareTo(BigDecimal.ZERO) > 0 && Boolean.TRUE.equals(invoice.getPaid())) {
            invoice.setPaid(false);
            changed = true;
        }
        return changed;
    }

    private record InvoiceTotals(BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal openAmount) {
    }

    @Override
    public List<AdvanceablePurchaseDTO> getAdvanceablePurchases(UUID cardId, Integer month, Integer year) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        CreditCard card = creditCardService.findById(cardId).orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado."));
        if (!card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Cartão não pertence ao usuário autenticado.");
        }

        // Calculate the close date epoch for the requested invoice month/year
        LocalDate closeLocal = calculateCloseDate(card, month, year);
        long closeEpoch = DateUtils.localDateToEpoch(closeLocal);

        // Use optimized repository method to fetch future unpaid invoices directly
        List<Invoices> futureInvoices = invoicesService.findFutureUnpaidByCardAndDate(currentUser.getId(), cardId, closeEpoch);

        if (futureInvoices == null || futureInvoices.isEmpty()) return List.of();

        List<UUID> invoiceIds = futureInvoices.stream().map(Invoices::getId).toList();

        // Fetch all advanceable installments in one query
        List<InstallmentPlan> advanceable = installmentPlanService.findAdvanceableByInvoiceIds(invoiceIds);

        if (advanceable == null || advanceable.isEmpty()) return List.of();

        List<AdvanceablePurchaseDTO> result = advanceable.stream()
                .collect(Collectors.groupingBy(InstallmentPlan::getPurchaseId))
                .entrySet().stream()
                .map(e -> {
                    UUID purchaseId = e.getKey();
                    List<InstallmentPlan> plans = e.getValue();
                    String name = plans.stream().findFirst().map(InstallmentPlan::getName).orElse("");
                    // remove suffixes like " (1/10)"
                    name = name.replaceAll(" \\([0-9]+/[0-9]+\\)$", "");
                    return AdvanceablePurchaseDTO.builder()
                            .purchaseId(purchaseId)
                            .name(name)
                            .maxInstallmentsAvailable(plans.size())
                            .estimatedAmount(plans.stream()
                                    .map(InstallmentPlan::getAmount)
                                    .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .build();
                })
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    @Transactional
    public void processRefund(UUID invoiceId, RefundRequestDTO request) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices invoice = invoicesService.findByIdOrThrow(invoiceId);

        if (!invoice.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Fatura não pertence ao usuário autenticado.");
        }

        InstallmentPlan original = installmentPlanService.findById(request.getInstallmentId())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Parcela não encontrada."));

        if (original.getInvoices() == null || !original.getInvoices().getId().equals(invoice.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela não pertence à fatura informada.");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do estorno deve ser maior que zero.");
        }
        if (original.getAmount() == null || original.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A parcela informada não permite estorno.");
        }

        BigDecimal alreadyRefunded = installmentPlanService.findByPurchaseId(original.getPurchaseId()).stream()
                .filter(i -> i.getDeletedAt() == null)
                .filter(i -> i.getInvoices() != null && i.getInvoices().getId().equals(invoice.getId()))
                .filter(i -> i.getAmount() != null && i.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(i -> i.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundableAmount = original.getAmount().abs().subtract(alreadyRefunded);
        if (request.getRefundAmount().compareTo(refundableAmount) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do estorno ultrapassa o saldo disponível da parcela.");
        }

        // CORREÇÃO MATEMÁTICA: Garante que o valor será negativo independente do que o front-end mandar
        BigDecimal refundAmount = request.getRefundAmount().abs().negate();

        InstallmentPlan reversal = InstallmentPlan.builder()
                .id(ID.generate())
                .date(DateUtils.getEpochNow())
                .name("Estorno: " + original.getName())
                .description(original.getDescription())
                .type(TransactionType.RECEITA.name()) // Transforma em crédito na fatura
                .amount(refundAmount)
                .totalInstallmentsPlan(1)
                .currentInstallment(1)
                .fixed(false)
                .paid(false)
                .purchaseId(original.getPurchaseId())
                .enabled(true)
                .createdAt(DateUtils.getEpochNow())
                .user(original.getUser())
                .invoices(invoice)
                .build();

        installmentPlanService.save(reversal);

        // Atualiza a Fatura (Subtrai adicionando o negativo)
        invoice.setAmount(invoice.getAmount().add(reversal.getAmount()));
        invoicesService.save(invoice);

        // Restaura o limite do cartão somando positivo
        CreditCard card = invoice.getCreditCard();
        card.restoreLimit(request.getRefundAmount().abs());
        creditCardService.updateLimit(card);
    }

    @Override
    @Transactional
    public void advanceInstallments(UUID invoiceId, AdvanceRequestDTO request) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices currentInvoice = invoicesService.findByIdOrThrow(invoiceId);

        if (!currentInvoice.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Fatura não pertence ao usuário autenticado.");
        }

        int limitToAdvance = request.getQuantityToAdvance() != null && request.getQuantityToAdvance() > 0 ? request.getQuantityToAdvance() : 1;

        // CORREÇÃO: Filtra deletados, pagos e garante ordenação por vencimento da fatura original
        List<InstallmentPlan> availableFutureInstallments = installmentPlanService.findByPurchaseId(request.getPurchaseId()).stream()
                .filter(i -> i.getDeletedAt() == null && !i.getPaid())
                .filter(i -> i.getInvoices().getExpirationDate() > currentInvoice.getExpirationDate())
                .sorted(Comparator.comparing(i -> i.getInvoices().getExpirationDate()))
                .collect(Collectors.toList());

        if (availableFutureInstallments.isEmpty()) {
            throw new BadRequestException("Aviso", "Não existem parcelas futuras pendentes para adiantar.");
        }
        if (limitToAdvance > availableFutureInstallments.size()) {
            throw new BadRequestException("Aviso", "Quantidade de parcelas para adiantar maior que o disponível.");
        }
        List<InstallmentPlan> futureInstallments = availableFutureInstallments.stream()
                .limit(limitToAdvance)
                .collect(Collectors.toList());

        BigDecimal totalAdvanced = BigDecimal.ZERO;

        for (InstallmentPlan inst : futureInstallments) {
            Invoices oldInv = inst.getInvoices();
            oldInv.setAmount(oldInv.getAmount().subtract(inst.getAmount()));
            invoicesService.save(oldInv);

            inst.setInvoices(currentInvoice);
            inst.setDate(currentInvoice.getExpirationDate());
            inst.setName(inst.getName() + " (Adiantada)");

            totalAdvanced = totalAdvanced.add(inst.getAmount());
        }

        installmentPlanService.saveAll(futureInstallments);

        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(totalAdvanced) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O desconto não pode ser maior que o total adiantado.");
        }

        // Lança o desconto de adiantamento, se houver
        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = request.getDiscountAmount().abs().negate();

            InstallmentPlan discountPlan = InstallmentPlan.builder()
                    .id(ID.generate())
                    .date(DateUtils.getEpochNow())
                    .name("Desconto Adiantamento")
                    .type(TransactionType.RECEITA.name())
                    .amount(discount)
                    .totalInstallmentsPlan(1)
                    .currentInstallment(1)
                    .fixed(false)
                    .paid(false)
                    .purchaseId(request.getPurchaseId())
                    .enabled(true)
                    .createdAt(DateUtils.getEpochNow())
                    .user(currentInvoice.getUser())
                    .invoices(currentInvoice)
                    .build();

            installmentPlanService.save(discountPlan);

            totalAdvanced = totalAdvanced.add(discount); // Abate o desconto do montante adiantado

            CreditCard card = currentInvoice.getCreditCard();
            card.restoreLimit(request.getDiscountAmount().abs());
            creditCardService.updateLimit(card);
        }

        currentInvoice.setAmount(currentInvoice.getAmount().add(totalAdvanced));
        invoicesService.save(currentInvoice);
    }

    @Override
    @Transactional
    public InvoiceDetailsDTO processPayment(UUID invoiceId, InvoicePaymentRequestDTO request) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices invoice = invoicesService.findByIdOrThrow(invoiceId);

        if (!invoice.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Fatura não pertence ao usuário autenticado.");
        }
        if (request.getAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Conta de pagamento não informada.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do pagamento deve ser maior que zero.");
        }

        List<InstallmentPlan> currentItems = installmentPlanService.findByInvoiceId(invoice.getId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toList());
        InvoiceTotals currentTotals = calculateTotals(currentItems);
        if (currentTotals.openAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Fatura não possui saldo em aberto.");
        }
        if (request.getAmount().compareTo(currentTotals.openAmount()) < 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O pagamento não pode ser menor que o saldo em aberto.");
        }

        Accounts sourceAccount = accountsService.findByIdOrThrow(request.getAccountId());
        if (!sourceAccount.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }
        if (sourceAccount.getType() == AccountType.CREDIT_CARD) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A conta de pagamento não pode ser uma conta de cartão de crédito.");
        }

        CreditCard card = invoice.getCreditCard();
        Accounts cardAccount = card.getAccounts();
        Category category = findPaymentCategory(currentUser);
        long now = DateUtils.getEpochNow();
        long paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : now;
        BigDecimal surchargeAmount = request.getAmount().subtract(currentTotals.openAmount());
        String notes = request.getNotes() != null ? request.getNotes() : "";
        if (surchargeAmount.compareTo(BigDecimal.ZERO) > 0) {
            notes = (notes.isBlank() ? "" : notes + " | ")
                    + "Acréscimo por juros/multa: R$ " + surchargeAmount;
        }

        Transactions paymentOut = Transactions.builder()
                .id(ID.generate())
                .name("Pagamento Fatura " + card.getName())
                .description(notes)
                .type(TransactionType.PAGAMENTO_FATURA)
                .amount(request.getAmount())
                .fixed(false)
                .paid(true)
                .enabled(true)
                .createdAt(now)
                .date(paymentDate)
                .account(sourceAccount)
                .category(category)
                .user(currentUser)
                .targetInvoice(invoice)
                .creditCard(card)
                .build();

        Transactions paymentIn = Transactions.builder()
                .id(ID.generate())
                .name("Recebimento de Fatura")
                .description(notes)
                .type(TransactionType.TRANSFERENCIA_ENTRADA)
                .amount(request.getAmount())
                .fixed(false)
                .paid(true)
                .enabled(true)
                .createdAt(now)
                .date(paymentDate)
                .account(cardAccount)
                .category(category)
                .user(currentUser)
                .targetInvoice(invoice)
                .creditCard(card)
                .parentTransaction(paymentOut)
                .build();

        transactionRepository.saveAll(List.of(paymentOut, paymentIn));

        sourceAccount.debit(request.getAmount());
        accountsService.update(sourceAccount);
        cardAccount.credit(request.getAmount());
        accountsService.update(cardAccount);

        card.restoreLimit(request.getAmount());
        creditCardService.updateLimit(card);

        InstallmentPlan paymentCredit = InstallmentPlan.builder()
                .id(ID.generate())
                .date(paymentDate)
                .name("Pagamento Recebido")
                .description(notes)
                .type(TransactionType.RECEITA.name())
                .amount(request.getAmount().abs().negate())
                .totalInstallmentsPlan(1)
                .currentInstallment(1)
                .fixed(false)
                .paid(true)
                .purchaseId(paymentOut.getId())
                .enabled(true)
                .createdAt(now)
                .user(currentUser)
                .invoices(invoice)
                .build();

        installmentPlanService.save(paymentCredit);

        List<InstallmentPlan> updatedItems = installmentPlanService.findByInvoiceId(invoice.getId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toList());
        if (updatedItems.stream().noneMatch(item -> item.getId().equals(paymentCredit.getId()))) {
            updatedItems.add(paymentCredit);
        }
        InvoiceTotals updatedTotals = calculateTotals(updatedItems);

        invoice.setAmount(updatedTotals.openAmount());
        invoice.setTransaction(paymentOut);
        if (updatedTotals.openAmount().compareTo(BigDecimal.ZERO) <= 0 && !isInvoiceOpenWindow(invoice)) {
            invoice.setPaid(true);
            updatedItems.forEach(inst -> inst.setPaid(true));
            installmentPlanService.saveAll(updatedItems);
        }
        invoicesService.save(invoice);

        return getInvoiceDetails(card.getId(), invoice.getMonth(), invoice.getYear())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura não encontrada."));
    }

    @Override
    @Transactional
    public InvoiceDetailsDTO cancelPayment(UUID paymentTransactionId) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Transactions payment = findInvoicePaymentTransaction(paymentTransactionId);

        if (payment.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Este pagamento já foi cancelado.");
        }
        if (!payment.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }
        if (payment.getType() != TransactionType.PAGAMENTO_FATURA || payment.getTargetInvoice() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Este lançamento não é um pagamento de fatura.");
        }

        Invoices invoice = payment.getTargetInvoice();
        if (invoice.getDeletedAt() != null) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura vinculada não encontrada.");
        }
        if (!invoice.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Fatura não pertence ao usuário autenticado.");
        }
        if (payment.getAccount() == null || payment.getAccount().getDeletedAt() != null) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Conta vinculada não encontrada.");
        }

        Transactions paymentIn = transactionRepository.findTransferChildByParentId(payment.getId())
                .orElse(null);
        if (paymentIn != null && (paymentIn.getAccount() == null || paymentIn.getAccount().getDeletedAt() != null)) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Conta vinculada não encontrada.");
        }

        try {
            long now = DateUtils.getEpochNow();
            reversePaymentBalance(payment, paymentIn);

            installmentPlanService.findByPurchaseId(payment.getId()).stream()
                    .filter(this::isPaymentItem)
                    .filter(item -> item.getDeletedAt() == null)
                    .forEach(item -> item.setDeletedAt(now));

            List<InstallmentPlan> invoiceItems = installmentPlanService.findByInvoiceId(invoice.getId());
            installmentPlanService.saveAll(invoiceItems);

            payment.setDeletedAt(now);
            if (paymentIn != null) {
                paymentIn.setDeletedAt(now);
                transactionRepository.saveAll(List.of(payment, paymentIn));
            } else {
                transactionRepository.save(payment);
            }

            List<InstallmentPlan> activeItems = invoiceItems.stream()
                    .filter(item -> item.getDeletedAt() == null)
                    .collect(Collectors.toList());
            InvoiceTotals totals = calculateTotals(activeItems);

            invoice.setAmount(totals.openAmount());
            if (invoice.getTransaction() != null && invoice.getTransaction().getId().equals(payment.getId())) {
                invoice.setTransaction(null);
            }
            invoice.setPaid(totals.openAmount().compareTo(BigDecimal.ZERO) <= 0 && !isInvoiceOpenWindow(invoice));
            invoicesService.save(invoice);

            return getInvoiceDetails(invoice.getCreditCard().getId(), invoice.getMonth(), invoice.getYear())
                    .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura não encontrada."));
        } catch (BadRequestException | EntityNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não foi possível cancelar o pagamento da fatura.");
        }
    }

    private Transactions findInvoicePaymentTransaction(UUID paymentOrInstallmentId) {
        Optional<Transactions> transaction = transactionRepository.findByIdIncludingDeleted(paymentOrInstallmentId);
        if (transaction.isPresent()) {
            return transaction.get();
        }

        InstallmentPlan paymentItem = installmentPlanService.findById(paymentOrInstallmentId)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Pagamento de fatura não encontrado."));

        if (!isPaymentItem(paymentItem) || paymentItem.getPurchaseId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Este lançamento não é um pagamento de fatura.");
        }

        return transactionRepository.findByIdIncludingDeleted(paymentItem.getPurchaseId())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Pagamento de fatura não encontrado."));
    }

    private void reversePaymentBalance(Transactions payment, Transactions paymentIn) {
        BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;

        if (Boolean.TRUE.equals(payment.getPaid())) {
            Accounts sourceAccount = payment.getAccount();
            sourceAccount.credit(amount);
            accountsService.update(sourceAccount);
        }

        if (paymentIn != null && Boolean.TRUE.equals(paymentIn.getPaid())) {
            Accounts cardAccount = paymentIn.getAccount();
            cardAccount.debit(amount);
            accountsService.update(cardAccount);
        }

        CreditCard card = payment.getCreditCard() != null ? payment.getCreditCard() : payment.getTargetInvoice().getCreditCard();
        if (card == null || card.getDeletedAt() != null) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura vinculada não encontrada.");
        }
        if (Boolean.TRUE.equals(payment.getPaid())) {
            card.setCurrentLimit(card.getCurrentLimit().subtract(amount));
            creditCardService.updateLimit(card);
        }
    }

    private Category findPaymentCategory(Users user) {
        try {
            return categoryService.findCategoryByUserAndName(user, "Transfêrencia");
        } catch (RuntimeException ignored) {
            return categoryService.findCategoryByUserAndName(user, "Reajuste de Saldo");
        }
    }

    private boolean isInvoiceOpenWindow(Invoices invoice) {
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        LocalDate closeDate = calculateCloseDate(invoice.getCreditCard(), invoice.getMonth(), invoice.getYear());
        LocalDate previousCloseDate = calculatePreviousCloseDate(invoice.getCreditCard(), invoice.getMonth(), invoice.getYear());
        return !today.isBefore(previousCloseDate) && today.isBefore(closeDate);
    }
}


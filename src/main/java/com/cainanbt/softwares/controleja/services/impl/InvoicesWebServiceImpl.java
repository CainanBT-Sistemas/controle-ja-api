package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
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
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDateService;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDomainValidator;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceTotalsCalculator;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceTotalsSummary;
import com.cainanbt.softwares.controleja.services.web.InvoicesWebService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicesWebServiceImpl implements InvoicesWebService {

    private final InvoiceDateService invoiceDateService = new InvoiceDateService();
    private final InvoiceTotalsCalculator invoiceTotalsCalculator = new InvoiceTotalsCalculator();
    private final InvoiceDomainValidator invoiceDomainValidator = new InvoiceDomainValidator();

    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final CreditCardService creditCardService;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    /**
     * Monta a visão detalhada da fatura com datas, status, totais e itens enriquecidos pela transação pai.
     */
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

            LocalDate closeLocal = invoiceDateService.calculateCloseDate(card, month, year);
            LocalDate expLocal = invoiceDateService.calculateExpirationDate(card, month, year);
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
                    .status(invoiceDateService.calculateInvoiceStatus(card, false, BigDecimal.ZERO, closeLocal, expLocal, month, year))
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

        List<InstallmentPlan> invoiceItems = installmentPlanService.findByInvoiceIdAndUserId(inv.getId(), currentUser.getId());
        Map<UUID, Transactions> parentTransactions = findParentTransactions(invoiceItems);
        List<InstallmentPlan> items = invoiceItems.stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !isInvoiceSummaryItem(p, parentTransactions))
                .sorted(Comparator.comparing(InstallmentPlan::getDate, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());

        InvoiceTotalsSummary totals = calculateInvoiceDetailsTotals(inv, items);

        LocalDate closeLocal = invoiceDateService.calculateCloseDate(inv.getCreditCard(), inv.getMonth(), inv.getYear());
        LocalDate expirationLocal = invoiceDateService.calculateExpirationDate(inv.getCreditCard(), inv.getMonth(), inv.getYear());
        Long closeDate = DateUtils.localDateToEpoch(closeLocal);
        Long expirationDate = DateUtils.localDateToEpoch(expirationLocal);
        String status = invoiceDateService.calculateInvoiceStatus(inv.getCreditCard(), inv.getPaid(), totals.openAmount(), closeLocal, expirationLocal, inv.getMonth(), inv.getYear());
        boolean closedOrPaid = invoiceDateService.isClosedOrPaid(status);

        List<InvoiceItemDTO> itemDTOs = items.stream()
                .map(i -> toInvoiceItemDTO(i, findParentTransaction(i, parentTransactions), closedOrPaid))
                .collect(Collectors.toList());

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

    private Map<UUID, Transactions> findParentTransactions(List<InstallmentPlan> invoiceItems) {
        List<UUID> purchaseIds = invoiceItems.stream()
                .map(InstallmentPlan::getPurchaseId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (purchaseIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Transactions> result = new HashMap<>();
        transactionRepository.findAllById(purchaseIds)
                .forEach(transaction -> result.put(transaction.getId(), transaction));
        return result;
    }

    private boolean isInvoiceSummaryItem(InstallmentPlan item, Map<UUID, Transactions> parentTransactions) {
        UUID purchaseId = item.getPurchaseId();
        if (purchaseId == null) {
            return false;
        }

        Transactions parent = parentTransactions.get(purchaseId);
        return parent != null && isPersistedInvoiceSummary(parent);
    }

    private boolean isPersistedInvoiceSummary(Transactions transaction) {
        String name = transaction.getName() != null ? transaction.getName().trim() : "";
        String categoryName = transaction.getCategory() != null ? transaction.getCategory().getName() : "";
        return transaction.getType() == TransactionType.DESPESA
                && name.startsWith("Fatura Cart")
                && "Fatura de Cartão".equalsIgnoreCase(categoryName);
    }

    private Transactions findParentTransaction(InstallmentPlan item, Map<UUID, Transactions> parentTransactions) {
        if (item.getPurchaseId() == null) {
            return null;
        }
        return parentTransactions.get(item.getPurchaseId());
    }

    private InvoiceItemDTO toInvoiceItemDTO(InstallmentPlan item, Transactions parentTransaction, boolean closedOrPaid) {
        String itemKind = resolveItemKind(item);
        return InvoiceItemDTO.builder()
                .id(item.getId())
                .transactionId(item.getPurchaseId())
                .purchaseId(item.getPurchaseId())
                .description(parentTransaction != null ? parentTransaction.getDescription() : item.getDescription())
                .date(item.getDate())
                .transactionDate(parentTransaction != null ? parentTransaction.getDate() : null)
                .name(item.getName())
                .categoryId(parentTransaction != null && parentTransaction.getCategory() != null ? parentTransaction.getCategory().getId() : null)
                .categoryName(resolveCategoryName(parentTransaction))
                .accountId(parentTransaction != null && parentTransaction.getAccount() != null ? parentTransaction.getAccount().getId() : null)
                .accountName(parentTransaction != null && parentTransaction.getAccount() != null ? parentTransaction.getAccount().getName() : null)
                .creditCardId(resolveCreditCardId(parentTransaction, item))
                .currentInstallment(item.getCurrentInstallment())
                .totalInstallmentsPlan(item.getTotalInstallmentsPlan())
                .type(item.getType())
                .amount(item.getAmount())
                .paid(item.getPaid())
                .fixed(parentTransaction != null ? parentTransaction.getFixed() : item.getFixed())
                .canEdit(!closedOrPaid && !Boolean.TRUE.equals(item.getPaid()) && "PURCHASE".equals(itemKind))
                .itemKind(itemKind)
                .build();
    }

    private String resolveCategoryName(Transactions parentTransaction) {
        if (parentTransaction == null || parentTransaction.getCategory() == null) return null;
        return parentTransaction.getCategory().getName();
    }

    private UUID resolveCreditCardId(Transactions parentTransaction, InstallmentPlan item) {
        if (parentTransaction != null && parentTransaction.getCreditCard() != null) {
            return parentTransaction.getCreditCard().getId();
        }
        if (item.getInvoices() != null && item.getInvoices().getCreditCard() != null) {
            return item.getInvoices().getCreditCard().getId();
        }
        return null;
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

    private InvoiceTotalsSummary calculateInvoiceDetailsTotals(Invoices invoice, List<InstallmentPlan> items) {
        return invoiceTotalsCalculator.calculateForDetails(invoice, items);
    }

    /**
     * Busca compras parceladas futuras que podem ser trazidas para a fatura selecionada.
     */
    @Override
    public List<AdvanceablePurchaseDTO> getAdvanceablePurchases(UUID cardId, Integer month, Integer year) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        CreditCard card = creditCardService.findById(cardId).orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado."));
        if (!card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Cartão não pertence ao usuário autenticado.");
        }

        LocalDate closeLocal = invoiceDateService.calculateCloseDate(card, month, year);
        long closeEpoch = DateUtils.localDateToEpoch(closeLocal);

        // Use optimized repository method to fetch future unpaid invoices directly
        List<Invoices> futureInvoices = invoicesService.findFutureUnpaidByCardAndDate(currentUser.getId(), cardId, closeEpoch);

        if (futureInvoices == null || futureInvoices.isEmpty()) return List.of();

        List<UUID> invoiceIds = futureInvoices.stream().map(Invoices::getId).toList();

        // Fetch all advanceable installments in one query
        List<InstallmentPlan> advanceable = installmentPlanService.findAdvanceableByInvoiceIdsAndUserId(invoiceIds, currentUser.getId());

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

    /**
     * Cria um item negativo de estorno na fatura e restaura o limite correspondente do cartão.
     */
    @Override
    @Transactional
    public void processRefund(UUID invoiceId, RefundRequestDTO request) {
        invoiceDomainValidator.validateRefundRequest(request);

        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices invoice = invoicesService.findByIdOrThrow(invoiceId);
        invoiceDomainValidator.validateInvoiceOwner(invoice, currentUser);

        InstallmentPlan original = installmentPlanService.findByIdAndUserIdOrThrow(request.getInstallmentId(), currentUser.getId());

        if (original.getInvoices() == null || !original.getInvoices().getId().equals(invoice.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela não pertence à fatura informada.");
        }
        if (original.getAmount() == null || original.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A parcela informada não permite estorno.");
        }
        if (original.getPurchaseId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela sem compra de origem para estornar.");
        }

        BigDecimal alreadyRefunded = installmentPlanService.findByPurchaseIdAndUserId(original.getPurchaseId(), currentUser.getId()).stream()
                .filter(i -> i.getDeletedAt() == null)
                .filter(i -> i.getInvoices() != null && i.getInvoices().getId().equals(invoice.getId()))
                .filter(i -> i.getAmount() != null && i.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(i -> i.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundableAmount = original.getAmount().abs().subtract(alreadyRefunded);
        if (request.getRefundAmount().compareTo(refundableAmount) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do estorno ultrapassa o saldo disponível da parcela.");
        }

        BigDecimal refundAmount = request.getRefundAmount().abs().negate();

        InstallmentPlan reversal = InstallmentPlan.builder()
                .id(ID.generate())
                .date(DateUtils.getEpochNow())
                .name("Estorno: " + original.getName())
                .description(original.getDescription())
                .type(TransactionType.RECEITA.name())
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

        invoice.setAmount(invoiceTotalsCalculator.valueOrZero(invoice.getAmount()).add(reversal.getAmount()));
        invoicesService.save(invoice);

        CreditCard card = invoiceDomainValidator.requireInvoiceCard(invoice);
        card.restoreLimit(request.getRefundAmount().abs());
        creditCardService.updateLimit(card);
        log.info("Invoice refund processed: invoiceId={}, installmentId={}, amount={}", invoiceId, original.getId(), request.getRefundAmount());
    }

    /**
     * Move parcelas futuras da compra para a fatura atual, aplicando desconto opcional antes de salvar alterações.
     */
    @Override
    @Transactional
    public void advanceInstallments(UUID invoiceId, AdvanceRequestDTO request) {
        invoiceDomainValidator.validateAdvanceRequest(request);

        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices currentInvoice = invoicesService.findByIdOrThrow(invoiceId);
        invoiceDomainValidator.validateInvoiceOwner(currentInvoice, currentUser);

        int limitToAdvance = request.getQuantityToAdvance() != null && request.getQuantityToAdvance() > 0 ? request.getQuantityToAdvance() : 1;

        List<InstallmentPlan> availableFutureInstallments = installmentPlanService.findByPurchaseIdAndUserId(request.getPurchaseId(), currentUser.getId()).stream()
                .filter(i -> invoiceDomainValidator.isAdvanceableFutureInstallment(i, currentInvoice, currentUser))
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

        BigDecimal totalAdvanced = futureInstallments.stream()
                .map(InstallmentPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(totalAdvanced) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O desconto não pode ser maior que o total adiantado.");
        }

        Map<UUID, Invoices> invoicesToSave = new HashMap<>();

        for (InstallmentPlan inst : futureInstallments) {
            Invoices oldInv = inst.getInvoices();
            oldInv.setAmount(invoiceTotalsCalculator.valueOrZero(oldInv.getAmount()).subtract(inst.getAmount()));
            invoicesToSave.put(oldInv.getId(), oldInv);

            inst.setInvoices(currentInvoice);
            inst.setDate(currentInvoice.getExpirationDate());
            inst.setName(inst.getName() + " (Adiantada)");
        }

        if (!invoicesToSave.isEmpty()) {
            invoicesService.saveAll(invoicesToSave.values().stream().toList());
        }
        installmentPlanService.saveAll(futureInstallments);

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

            totalAdvanced = totalAdvanced.add(discount);

            CreditCard card = invoiceDomainValidator.requireInvoiceCard(currentInvoice);
            card.restoreLimit(request.getDiscountAmount().abs());
            creditCardService.updateLimit(card);
        }

        currentInvoice.setAmount(invoiceTotalsCalculator.valueOrZero(currentInvoice.getAmount()).add(totalAdvanced));
        invoicesService.save(currentInvoice);
        log.info("Invoice installments advanced: invoiceId={}, purchaseId={}, quantity={}, netAmount={}", invoiceId, request.getPurchaseId(), futureInstallments.size(), totalAdvanced);
    }

    /**
     * Atualiza uma parcela pela rota de fatura, validando vínculo com a fatura antes de delegar o escopo ao fluxo de transações.
     */
    @Override
    @Transactional
    public InvoiceDetailsDTO updateInvoiceItem(UUID invoiceId, UUID installmentId, TransactionDTO request, OperationScope operationScope) {
        Invoices invoice = resolveEditableInvoice(invoiceId);
        InstallmentPlan installment = resolveInvoiceInstallment(invoice, installmentId);

        transactionService.updateTransactionDTO(installment.getId(), request, operationScope);
        log.info("Invoice item updated: invoiceId={}, installmentId={}, scope={}", invoiceId, installmentId, operationScope);
        return refreshInvoiceDetails(invoice);
    }

    /**
     * Remove uma parcela pela rota de fatura, respeitando ONLY_THIS, FROM_THIS_FORWARD ou ALL.
     */
    @Override
    @Transactional
    public InvoiceDetailsDTO deleteInvoiceItem(UUID invoiceId, UUID installmentId, OperationScope operationScope) {
        Invoices invoice = resolveEditableInvoice(invoiceId);
        InstallmentPlan installment = resolveInvoiceInstallment(invoice, installmentId);

        transactionService.softDelete(installment.getId(), operationScope);
        log.info("Invoice item deleted: invoiceId={}, installmentId={}, scope={}", invoiceId, installmentId, operationScope);
        return refreshInvoiceDetails(invoice);
    }

    /**
     * Cancela a compra inteira a partir do purchaseId quando a compra pertence à fatura e não possui parcelas pagas.
     */
    @Override
    @Transactional
    public InvoiceDetailsDTO cancelPurchase(UUID invoiceId, UUID purchaseId) {
        Invoices invoice = resolveEditableInvoice(invoiceId);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        List<InstallmentPlan> installments = installmentPlanService.findActiveByPurchaseIdAndUserId(purchaseId, currentUser.getId()).stream()
                .sorted(Comparator.comparing(InstallmentPlan::getCurrentInstallment))
                .toList();

        if (installments.isEmpty()) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Compra não encontrada.");
        }
        boolean belongsToInvoice = installments.stream()
                .anyMatch(installment -> installment.getInvoices() != null && installment.getInvoices().getId().equals(invoice.getId()));
        if (!belongsToInvoice) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra não pertence à fatura informada.");
        }

        InstallmentPlan reference = installments.get(0);
        transactionService.softDelete(reference.getId(), OperationScope.ALL);
        log.info("Invoice purchase cancelled: invoiceId={}, purchaseId={}, installments={}", invoiceId, purchaseId, installments.size());
        return refreshInvoiceDetails(invoice);
    }

    /**
     * Registra pagamento integral da fatura, movimentando conta origem, conta do cartão, limite e item de crédito.
     */
    @Override
    @Transactional
    public InvoiceDetailsDTO processPayment(UUID invoiceId, InvoicePaymentRequestDTO request) {
        invoiceDomainValidator.validatePaymentRequest(request);

        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices invoice = invoicesService.findByIdOrThrow(invoiceId);
        invoiceDomainValidator.validateInvoiceOwner(invoice, currentUser);

        List<InstallmentPlan> currentItems = installmentPlanService.findByInvoiceIdAndUserId(invoice.getId(), currentUser.getId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toList());
        InvoiceTotalsSummary currentTotals = invoiceTotalsCalculator.calculate(currentItems);
        if (currentTotals.openAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Fatura não possui saldo em aberto.");
        }
        if (request.getAmount().compareTo(currentTotals.openAmount()) < 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O pagamento não pode ser menor que o saldo em aberto.");
        }

        Accounts sourceAccount = accountsService.findByIdOrThrow(request.getAccountId());
        invoiceDomainValidator.validatePaymentSourceAccount(sourceAccount, currentUser);

        CreditCard card = invoiceDomainValidator.requireInvoiceCard(invoice);
        Accounts cardAccount = invoiceDomainValidator.requireCardAccount(card);
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

        List<InstallmentPlan> updatedItems = installmentPlanService.findByInvoiceIdAndUserId(invoice.getId(), currentUser.getId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toList());
        if (updatedItems.stream().noneMatch(item -> item.getId().equals(paymentCredit.getId()))) {
            updatedItems.add(paymentCredit);
        }
        InvoiceTotalsSummary updatedTotals = invoiceTotalsCalculator.calculate(updatedItems);

        invoice.setAmount(updatedTotals.openAmount());
        invoice.setTransaction(paymentOut);
        if (updatedTotals.openAmount().compareTo(BigDecimal.ZERO) <= 0 && !invoiceDateService.isInvoiceOpenWindow(invoice)) {
            invoice.setPaid(true);
            updatedItems.forEach(inst -> inst.setPaid(true));
            installmentPlanService.saveAll(updatedItems);
        }
        invoicesService.save(invoice);
        log.info("Invoice payment processed: invoiceId={}, accountId={}, amount={}, openAmount={}", invoiceId, sourceAccount.getId(), request.getAmount(), updatedTotals.openAmount());

        return getInvoiceDetails(card.getId(), invoice.getMonth(), invoice.getYear())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura não encontrada."));
    }

    /**
     * Cancela um pagamento de fatura e reverte os impactos em saldos, limite e itens de pagamento.
     */
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

            List<InstallmentPlan> cancelledPaymentItems = installmentPlanService.findByPurchaseIdAndUserId(payment.getId(), currentUser.getId()).stream()
                    .filter(this::isPaymentItem)
                    .filter(item -> item.getDeletedAt() == null)
                    .peek(item -> item.setDeletedAt(now))
                    .collect(Collectors.toList());
            if (!cancelledPaymentItems.isEmpty()) {
                installmentPlanService.saveAll(cancelledPaymentItems);
            }

            List<InstallmentPlan> invoiceItems = installmentPlanService.findByInvoiceIdAndUserId(invoice.getId(), currentUser.getId());

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
            InvoiceTotalsSummary totals = invoiceTotalsCalculator.calculate(activeItems);

            invoice.setAmount(totals.openAmount());
            if (invoice.getTransaction() != null && invoice.getTransaction().getId().equals(payment.getId())) {
                invoice.setTransaction(null);
            }
            invoice.setPaid(totals.openAmount().compareTo(BigDecimal.ZERO) <= 0 && !invoiceDateService.isInvoiceOpenWindow(invoice));
            invoicesService.save(invoice);
            log.info("Invoice payment cancelled: invoiceId={}, paymentTransactionId={}, openAmount={}", invoice.getId(), payment.getId(), totals.openAmount());

            return getInvoiceDetails(invoice.getCreditCard().getId(), invoice.getMonth(), invoice.getYear())
                    .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura não encontrada."));
        } catch (BadRequestException | EntityNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to cancel invoice payment: paymentTransactionId={}", paymentTransactionId, e);
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

    private Invoices resolveEditableInvoice(UUID invoiceId) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Invoices invoice = invoicesService.findByIdOrThrow(invoiceId);
        invoiceDomainValidator.validateInvoiceOwner(invoice, currentUser);
        invoiceDomainValidator.validateEditableInvoice(invoice);
        return invoice;
    }

    private InstallmentPlan resolveInvoiceInstallment(Invoices invoice, UUID installmentId) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        InstallmentPlan installment = installmentPlanService.findByIdAndUserIdOrThrow(installmentId, currentUser.getId());

        if (installment.getInvoices() == null || !installment.getInvoices().getId().equals(invoice.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela não pertence à fatura informada.");
        }
        invoiceDomainValidator.validateEditableInstallment(installment);
        return installment;
    }

    private InvoiceDetailsDTO refreshInvoiceDetails(Invoices invoice) {
        Invoices refreshedInvoice = invoicesService.findByIdOrThrow(invoice.getId());
        return getInvoiceDetails(refreshedInvoice.getCreditCard().getId(), refreshedInvoice.getMonth(), refreshedInvoice.getYear())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Fatura não encontrada."));
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

}


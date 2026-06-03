package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.responses.TransactionResponseDTO;
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
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.processors.TransactionHelper;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessor;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessorFactory;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.OdometerValidator;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final RecurrenceRuleService recurrenceRuleService;
    private final TransactionProcessorFactory processorFactory;
    private final TransactionHelper helper;
    private final GasStationRankingService gasStationRankingService;
    private final VehicleService vehicleService;
    private final VehicleLogRepository vehicleLogRepository;

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
        validateCategoryForTransaction(category);
        validateVehicleOdometerOnCreate(dto, user);

        TransactionProcessor processor = processorFactory.getProcessor(dto, account);

        // Salva a transação atual e a regra de recorrência
        Transactions savedTransaction = processor.process(dto, account, category, user);

        processVehicleMetricsIfApplicable(savedTransaction, dto);

        // CORREÇÃO: Roda na mesma thread. É tão rápido (5ms) que não vai travar o celular.
        // Como roda dentro da mesma transação, o banco enxerga a regra que acabou de ser criada!
        if (savedTransaction.getRecurrenceRule() != null && Boolean.TRUE.equals(dto.getIsFixed())) {
            LocalDate limiteProjecao = LocalDate.now(DateUtils.zoneId).plusYears(1);
            generateProjectionsForRule(savedTransaction.getRecurrenceRule(), limiteProjecao);
        }

        return savedTransaction;
    }

    private void processVehicleMetricsIfApplicable(Transactions tx, TransactionDTO dto) {
        if (tx.getVehicle() != null) {
            // Se for abastecimento (tem posto e litros), atualiza o ranking de postos
            if (tx.getGasStation() != null && tx.getLiters() != null && tx.getLiters() > 0) {
                // Como você já tem a lógica prontinha no GasStationRankingService!
                gasStationRankingService.updateRanking(tx);
            }
        }
    }

    @Override
    public List<TransactionResponseDTO> listLastTransactionsDTO(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start == null || end == null) return Collections.emptyList();

        List<Transactions> normalTx = repository.findCashFlowTransactionsByMonth(user.getId(), start, end);
        List<TransactionResponseDTO> responseList = new ArrayList<>(normalTx.stream().map(TransactionResponseDTO::toDTO).toList());

        applyCreditCardInvoices(responseList, user.getId(), start, end);

        responseList.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return responseList;
    }

    private void applyCreditCardInvoices(List<TransactionResponseDTO> responseList, UUID userId, Long start, Long end) {
        List<Invoices> invoices = invoicesService.findByUserAndDateBetween(userId, start, end);
        for (Invoices inv : invoices) {
            if (inv.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            TransactionResponseDTO dto = new TransactionResponseDTO();
            dto.setId(inv.getId());

            String monthName = Month.of(inv.getMonth()).getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
            String formattedMonth = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
            dto.setName("Fatura " + inv.getCreditCard().getName() + " - " + formattedMonth);

            dto.setAmount(inv.getAmount());
            dto.setDate(inv.getExpirationDate());
            dto.setPaid(inv.getPaid());
            dto.setType(TransactionType.DESPESA);

            dto.setAccountId(inv.getCreditCard().getAccounts().getId());
            dto.setAccountName(inv.getCreditCard().getName());
            dto.setCategoryName("Fatura de Cartão");

            responseList.add(dto);
        }
    }

    @Override
    @Transactional
    public TransactionResponseDTO updateTransactionDTO(UUID id, TransactionDTO dto, OperationScope operationScope) {
        OperationScope scope = normalizeScope(operationScope);
        Optional<Invoices> invOpt = invoicesService.findById(id);

        if (invOpt.isPresent()) {
            Invoices inv = invOpt.get();

            if (dto.getPaid() != null) {
                inv.setPaid(dto.getPaid());
                invoicesService.save(inv);

                List<InstallmentPlan> installments = installmentPlanService.findByInvoiceId(inv.getId());
                installments.forEach(inst -> inst.setPaid(dto.getPaid()));
                installmentPlanService.saveAll(installments);
            }

            TransactionResponseDTO resp = new TransactionResponseDTO();
            resp.setId(inv.getId());
            resp.setName("Fatura " + inv.getCreditCard().getName());
            resp.setAmount(inv.getAmount());
            resp.setDate(inv.getExpirationDate());
            resp.setPaid(inv.getPaid());
            resp.setType(TransactionType.DESPESA);
            return resp;
        }

        Optional<InstallmentPlan> instOpt = installmentPlanService.findById(id);
        if (instOpt.isPresent()) {
            return updateCreditCardInstallments(instOpt.get(), dto, scope);
        }

        Transactions current = findByIdOrThrow(id);
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(current.getId());
        if (current.getAccount().getType() == AccountType.CREDIT_CARD && !installments.isEmpty()) {
            return updateCreditCardInstallments(current, installments, dto, scope, null);
        }

        if (isTransferSide(current)) {
            return TransactionResponseDTO.toDTO(updateTransferPair(current, dto, scope));
        }

        Transactions transaction = updateTransaction(id, dto, scope);

        return TransactionResponseDTO.toDTO(transaction);
    }

    private TransactionResponseDTO updateCreditCardInstallments(InstallmentPlan reference, TransactionDTO dto) {
        return updateCreditCardInstallments(reference, dto, OperationScope.ONLY_THIS);
    }

    private TransactionResponseDTO updateCreditCardInstallments(InstallmentPlan reference, TransactionDTO dto, OperationScope scope) {
        if (reference.getPurchaseId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela sem compra vinculada.");
        }

        Transactions purchase = repository.findById(reference.getPurchaseId()).orElse(null);
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(reference.getPurchaseId());
        if (installments.isEmpty()) {
            installments = List.of(reference);
        }
        return updateCreditCardInstallments(purchase, installments, dto, scope, reference);
    }

    private TransactionResponseDTO updateCreditCardInstallments(Transactions purchase, List<InstallmentPlan> installments, TransactionDTO dto) {
        return updateCreditCardInstallments(purchase, installments, dto, OperationScope.ALL, null);
    }

    private TransactionResponseDTO updateCreditCardInstallments(Transactions purchase, List<InstallmentPlan> installments, TransactionDTO dto, OperationScope scope, InstallmentPlan reference) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        boolean ownsPurchase = purchase != null && purchase.getUser() != null && purchase.getUser().getId().equals(currentUser.getId());
        boolean ownsInstallment = installments.stream().anyMatch(inst -> inst.getUser() != null && inst.getUser().getId().equals(currentUser.getId()));
        if (!ownsPurchase && !ownsInstallment) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        List<InstallmentPlan> activeInstallments = installments.stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .sorted((a, b) -> a.getCurrentInstallment().compareTo(b.getCurrentInstallment()))
                .toList();
        if (activeInstallments.isEmpty()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra sem parcelas ativas para atualizar.");
        }

        List<InstallmentPlan> scopedInstallments = selectInstallmentsForScope(activeInstallments, scope, reference);
        validateEditableInstallments(scopedInstallments);

        List<InstallmentPlan> installmentsToUpdate = new ArrayList<>();
        BigDecimal totalBeforeAmountChange = installments.stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .map(InstallmentPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (dto.getInstallments() != null) {
            if (scope != OperationScope.ALL) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração da quantidade de parcelas só pode ser aplicada ao lançamento inteiro.");
            }
            return updateCreditCardInstallmentCount(purchase, installments, dto, dateNow);
        }

        CreditCard targetCard = resolveTargetCard(dto, purchase, activeInstallments);
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();
        int referenceInstallment = reference != null
                ? reference.getCurrentInstallment()
                : activeInstallments.get(0).getCurrentInstallment();

        for (InstallmentPlan inst : scopedInstallments) {
            BigDecimal oldAmount = inst.getAmount();
            boolean changed = false;

            if (dto.getAmount() != null && dto.getAmount().compareTo(oldAmount) != 0) {
                inst.setAmount(dto.getAmount());
                changed = true;

                applyInvoiceDelta(invoicesToUpdate, inst.getInvoices(), dto.getAmount().subtract(oldAmount), dateNow);
            }
            if (dto.getName() != null) {
                inst.setName(buildInstallmentName(dto.getName(), inst));
                changed = true;
            }
            if (dto.getDescription() != null) {
                inst.setDescription(dto.getDescription());
                changed = true;
            }
            if (dto.getType() != null) {
                inst.setType(dto.getType().name());
                changed = true;
            }
            if (dto.getDate() != null || targetCardChanged(targetCard, inst)) {
                LocalDateTime referenceDate = DateUtils.epochToLocalDateTime(dto.getDate() != null ? dto.getDate() : purchase.getDate());
                int monthsToAdd = scope == OperationScope.ONLY_THIS ? 0 : inst.getCurrentInstallment() - referenceInstallment;
                moveInstallmentToInvoice(inst, targetCard, referenceDate.plusMonths(monthsToAdd), invoicesToUpdate, dateNow);
                changed = true;
            }

            if (changed) {
                inst.setUpdatedAt(dateNow);
                installmentsToUpdate.add(inst);
            }
        }

        if (!installmentsToUpdate.isEmpty()) {
            installmentPlanService.saveAll(installmentsToUpdate);
        }
        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }

        if (purchase != null) {
            if (purchase.getUser() == null || !purchase.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
            }
            if (!installmentsToUpdate.isEmpty() && dto.getAmount() != null) {
                BigDecimal activeTotal = installments.stream()
                        .filter(inst -> inst.getDeletedAt() == null)
                        .map(InstallmentPlan::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                purchase.setAmount(activeTotal);
            }
            if (scope == OperationScope.ALL && !installmentsToUpdate.isEmpty() && dto.getName() != null)
                purchase.setName(dto.getName());
            if (!installmentsToUpdate.isEmpty() && dto.getDescription() != null)
                purchase.setDescription(dto.getDescription());
            if (!installmentsToUpdate.isEmpty() && dto.getType() != null) purchase.setType(dto.getType());
            if (scope == OperationScope.ALL && dto.getCategoryId() != null) {
                Category category = categoryService.findByIdOrThrow(dto.getCategoryId());
                validateCategoryForTransaction(category);
                purchase.setCategory(category);
            }
            if (targetCard != null && purchase.getCreditCard() != null && !targetCard.getId().equals(purchase.getCreditCard().getId())) {
                purchase.setCreditCard(targetCard);
                purchase.setAccount(targetCard.getAccounts());
            }
            if (dto.getDate() != null) {
                purchase.setDate(dto.getDate());
            }
            if (!installmentsToUpdate.isEmpty() || dto.getDate() != null) {
                purchase.setUpdatedAt(dateNow);
                purchase = repository.save(purchase);
            }
            if (!installmentsToUpdate.isEmpty() && dto.getAmount() != null && purchase.getCreditCard() != null) {
                BigDecimal activeTotal = installments.stream()
                        .filter(inst -> inst.getDeletedAt() == null)
                        .map(InstallmentPlan::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                adjustCreditCardLimitForPurchaseAmountChange(purchase.getCreditCard(), totalBeforeAmountChange, activeTotal);
            }
            return TransactionResponseDTO.toDTO(purchase);
        }

        return buildInstallmentResponse(installmentsToUpdate.stream().findFirst().orElse(installments.get(0)));
    }

    private void adjustCreditCardLimitForPurchaseAmountChange(CreditCard card, BigDecimal oldTotal, BigDecimal newTotal) {
        BigDecimal difference = newTotal.subtract(oldTotal);
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            card.consumeLimit(difference);
            creditCardService.updateLimit(card);
        } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
            card.restoreLimit(difference.abs());
            creditCardService.updateLimit(card);
        }
    }

    private TransactionResponseDTO updateCreditCardInstallmentCount(Transactions purchase, List<InstallmentPlan> installments, TransactionDTO dto, long dateNow) {
        if (purchase == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra original não encontrada para recalcular as parcelas.");
        }
        if (dto.getInstallments() < 1) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O número mínimo de parcelas é 1.");
        }

        List<InstallmentPlan> activeInstallments = installments.stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .sorted((a, b) -> a.getCurrentInstallment().compareTo(b.getCurrentInstallment()))
                .toList();

        if (activeInstallments.isEmpty()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra sem parcelas ativas para recalcular.");
        }
        if (activeInstallments.stream().anyMatch(this::isPaidInvoiceInstallment)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque a compra possui parcela em fatura paga.");
        }
        if (dto.getInstallments() > activeInstallments.size()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível aumentar a quantidade de parcelas após a compra criada.");
        }

        BigDecimal installmentAmount = purchase.getAmount().divide(BigDecimal.valueOf(dto.getInstallments()), 2, RoundingMode.DOWN);
        BigDecimal difference = purchase.getAmount().subtract(installmentAmount.multiply(BigDecimal.valueOf(dto.getInstallments())));
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();
        List<InstallmentPlan> installmentsToUpdate = new ArrayList<>();
        String baseName = dto.getName() != null ? dto.getName() : removeInstallmentSuffix(activeInstallments.get(0).getName());

        for (int i = 0; i < activeInstallments.size(); i++) {
            InstallmentPlan inst = activeInstallments.get(i);
            Invoices invoice = invoicesToUpdate.getOrDefault(inst.getInvoices().getId(), inst.getInvoices());

            if (i >= dto.getInstallments()) {
                inst.setDeletedAt(dateNow);
                inst.setUpdatedAt(dateNow);
                invoice.setAmount(invoice.getAmount().subtract(inst.getAmount()));
                invoice.setUpdatedAt(dateNow);
                installmentsToUpdate.add(inst);
                invoicesToUpdate.put(invoice.getId(), invoice);
                continue;
            }

            BigDecimal oldAmount = inst.getAmount();
            BigDecimal newAmount = i == 0 ? installmentAmount.add(difference) : installmentAmount;
            inst.setAmount(newAmount);
            inst.setTotalInstallmentsPlan(dto.getInstallments());
            inst.setCurrentInstallment(i + 1);
            inst.setName(buildInstallmentName(baseName, i + 1, dto.getInstallments()));
            if (dto.getDescription() != null) {
                inst.setDescription(dto.getDescription());
            }
            if (dto.getType() != null) {
                inst.setType(dto.getType().name());
            }
            inst.setUpdatedAt(dateNow);

            invoice.setAmount(invoice.getAmount().add(newAmount.subtract(oldAmount)));
            invoice.setUpdatedAt(dateNow);
            installmentsToUpdate.add(inst);
            invoicesToUpdate.put(invoice.getId(), invoice);
        }

        installmentPlanService.saveAll(installmentsToUpdate);
        invoicesService.saveAll(invoicesToUpdate.values().stream().toList());

        if (dto.getName() != null) purchase.setName(dto.getName());
        if (dto.getDescription() != null) purchase.setDescription(dto.getDescription());
        if (dto.getType() != null) purchase.setType(dto.getType());
        purchase.setUpdatedAt(dateNow);
        purchase = repository.save(purchase);

        return TransactionResponseDTO.toDTO(purchase);
    }

    private void validateCreditCardPurchaseDateChange(Transactions purchase, List<InstallmentPlan> installments, Long newDate) {
        if (purchase == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra original não encontrada para validar a data.");
        }

        List<InstallmentPlan> activeInstallments = installments.stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .toList();

        if (activeInstallments.stream().anyMatch(this::isPaidInvoiceInstallment)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar a data porque a compra possui parcela em fatura paga.");
        }

        for (InstallmentPlan inst : activeInstallments) {
            Invoices currentInvoice = inst.getInvoices();
            if (currentInvoice == null || currentInvoice.getCreditCard() == null) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela sem fatura ou cartão vinculado para validar a data.");
            }

            LocalDateTime newPurchaseDate = DateUtils.epochToLocalDateTime(newDate);
            LocalDateTime newInvoiceDate = helper.calculateInvoiceDate(
                    newPurchaseDate.plusMonths(inst.getCurrentInstallment() - 1L),
                    currentInvoice.getCreditCard().getCloseDay(),
                    currentInvoice.getCreditCard().getBestDay()
            );

            if (currentInvoice.getMonth() == null
                    || currentInvoice.getYear() == null
                    || currentInvoice.getMonth() != newInvoiceDate.getMonthValue()
                    || currentInvoice.getYear() != newInvoiceDate.getYear()) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A nova data altera a fatura da compra. Escolha uma data dentro do período da fatura atual.");
            }
        }
    }

    private boolean isPaidInvoiceInstallment(InstallmentPlan installment) {
        return Boolean.TRUE.equals(installment.getPaid())
                || (installment.getInvoices() != null && Boolean.TRUE.equals(installment.getInvoices().getPaid()));
    }

    private OperationScope normalizeScope(OperationScope scope) {
        if (scope != null) {
            return scope;
        }
        return OperationScope.ONLY_THIS;
    }

    private List<InstallmentPlan> selectInstallmentsForScope(List<InstallmentPlan> installments, OperationScope scope, InstallmentPlan reference) {
        if (scope == OperationScope.ALL) {
            return installments;
        }
        if (reference == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Informe a parcela de referência para aplicar este escopo.");
        }
        int current = reference.getCurrentInstallment();
        if (scope == OperationScope.FROM_THIS_FORWARD) {
            return installments.stream()
                    .filter(inst -> inst.getCurrentInstallment() >= current)
                    .toList();
        }
        return installments.stream()
                .filter(inst -> inst.getId().equals(reference.getId()))
                .toList();
    }

    private void validateEditableInstallments(List<InstallmentPlan> installments) {
        for (InstallmentPlan inst : installments) {
            Invoices invoice = inst.getInvoices();
            if (Boolean.TRUE.equals(inst.getPaid())
                    || invoice == null
                    || Boolean.TRUE.equals(invoice.getPaid())
                    || Boolean.FALSE.equals(invoice.getEnabled())
                    || invoice.getDeletedAt() != null) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar parcela vinculada a fatura paga, fechada ou bloqueada.");
            }
        }
    }

    private CreditCard resolveTargetCard(TransactionDTO dto, Transactions purchase, List<InstallmentPlan> installments) {
        if (dto.getCreditCardId() != null) {
            return creditCardService.findByIdOrThrow(dto.getCreditCardId());
        }
        if (dto.getAccountId() != null) {
            return creditCardService.findByAccountId(dto.getAccountId());
        }
        if (purchase != null && purchase.getCreditCard() != null) {
            return purchase.getCreditCard();
        }
        return installments.get(0).getInvoices().getCreditCard();
    }

    private boolean targetCardChanged(CreditCard targetCard, InstallmentPlan installment) {
        return targetCard != null
                && installment.getInvoices() != null
                && installment.getInvoices().getCreditCard() != null
                && !targetCard.getId().equals(installment.getInvoices().getCreditCard().getId());
    }

    private void moveInstallmentToInvoice(InstallmentPlan inst, CreditCard targetCard, LocalDateTime purchaseDate, Map<UUID, Invoices> invoicesToUpdate, long dateNow) {
        Invoices oldInvoice = inst.getInvoices();
        validateEditableInvoice(oldInvoice);

        LocalDateTime newInvoiceDate = helper.calculateInvoiceDate(
                purchaseDate,
                targetCard.getCloseDay(),
                targetCard.getBestDay()
        );
        if (oldInvoice.getCreditCard() != null
                && oldInvoice.getCreditCard().getId().equals(targetCard.getId())
                && oldInvoice.getMonth() != null
                && oldInvoice.getYear() != null
                && oldInvoice.getMonth() == newInvoiceDate.getMonthValue()
                && oldInvoice.getYear() == newInvoiceDate.getYear()) {
            inst.setDate(oldInvoice.getExpirationDate());
            return;
        }
        Invoices newInvoice = invoicesService.findByCreditCardIdAndMonthAndYear(targetCard.getId(), newInvoiceDate.getMonthValue(), newInvoiceDate.getYear())
                .orElseGet(() -> invoicesService.save(Invoices.builder()
                        .id(ID.generate())
                        .month(newInvoiceDate.getMonthValue())
                        .year(newInvoiceDate.getYear())
                        .amount(BigDecimal.ZERO)
                        .expirationDate(DateUtils.localDateTimeToEpoch(newInvoiceDate))
                        .paid(false)
                        .enabled(true)
                        .createdAt(dateNow)
                        .creditCard(targetCard)
                        .user(inst.getUser())
                        .build()));
        validateEditableInvoice(newInvoice);

        if (!oldInvoice.getId().equals(newInvoice.getId())) {
            applyInvoiceDelta(invoicesToUpdate, oldInvoice, inst.getAmount().negate(), dateNow);
            applyInvoiceDelta(invoicesToUpdate, newInvoice, inst.getAmount(), dateNow);
            inst.setInvoices(newInvoice);
        }
        inst.setDate(newInvoice.getExpirationDate());
    }

    private void validateEditableInvoice(Invoices invoice) {
        if (invoice == null
                || Boolean.TRUE.equals(invoice.getPaid())
                || Boolean.FALSE.equals(invoice.getEnabled())
                || invoice.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar fatura paga, fechada ou bloqueada para edição.");
        }
    }

    private void applyInvoiceDelta(Map<UUID, Invoices> invoicesToUpdate, Invoices invoice, BigDecimal delta, long dateNow) {
        Invoices target = invoicesToUpdate.getOrDefault(invoice.getId(), invoice);
        target.setAmount(target.getAmount().add(delta));
        target.setUpdatedAt(dateNow);
        invoicesToUpdate.put(target.getId(), target);
    }

    private String buildInstallmentName(String name, InstallmentPlan installment) {
        if (installment.getTotalInstallmentsPlan() > 1) {
            return name + " (" + installment.getCurrentInstallment() + "/" + installment.getTotalInstallmentsPlan() + ")";
        }
        return name;
    }

    private String buildInstallmentName(String name, int currentInstallment, int totalInstallments) {
        if (totalInstallments > 1) {
            return name + " (" + currentInstallment + "/" + totalInstallments + ")";
        }
        return name;
    }

    private String removeInstallmentSuffix(String name) {
        if (name == null) {
            return null;
        }
        return name.replaceFirst("\\s*\\(\\d+/\\d+\\)$", "");
    }

    private TransactionResponseDTO buildInstallmentResponse(InstallmentPlan installment) {
        TransactionResponseDTO response = new TransactionResponseDTO();
        response.setId(installment.getId());
        response.setName(installment.getName());
        response.setAmount(installment.getAmount());
        response.setDate(installment.getDate());
        response.setPaid(installment.getPaid());
        response.setType(TransactionType.valueOf(installment.getType()));
        return response;
    }

    @Override
    public List<TransactionResponseDTO> getTransactionsTypeVehicle(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        List<Transactions> transactions = repository.findTransactionsByMonth(user.getId(), start, end);
        List<TransactionResponseDTO> directVehicleExpenses = transactions.stream()
                .filter(tx -> tx.getVehicle() != null)
                .filter(tx -> tx.getType() == TransactionType.DESPESA)
                .filter(tx -> tx.getAccount() == null || tx.getAccount().getType() != AccountType.CREDIT_CARD)
                .map(TransactionResponseDTO::toDTO)
                .toList();

        List<InstallmentPlan> vehicleInstallments = installmentPlanService.findVehicleInstallmentsByUserAndDateBetween(user.getId(), start, end);
        List<UUID> purchaseIds = vehicleInstallments.stream()
                .map(InstallmentPlan::getPurchaseId)
                .distinct()
                .toList();
        Map<UUID, Transactions> purchasesById = repository.findAllById(purchaseIds).stream()
                .collect(java.util.stream.Collectors.toMap(Transactions::getId, transaction -> transaction));

        List<TransactionResponseDTO> installmentVehicleExpenses = vehicleInstallments.stream()
                .map(installment -> buildVehicleInstallmentResponse(installment, purchasesById.get(installment.getPurchaseId())))
                .toList();

        List<TransactionResponseDTO> response = new ArrayList<>(directVehicleExpenses.size() + installmentVehicleExpenses.size());
        response.addAll(directVehicleExpenses);
        response.addAll(installmentVehicleExpenses);

        return response.stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .toList();
    }

    /**
     * Monta um item de detalhe de veículo a partir da parcela mensal, preservando a origem na fatura.
     */
    private TransactionResponseDTO buildVehicleInstallmentResponse(InstallmentPlan installment, Transactions purchase) {
        TransactionResponseDTO response = buildInstallmentResponse(installment);
        response.setVirtual(false);
        if (installment.getInvoices() != null) {
            response.setTargetInvoiceId(installment.getInvoices().getId());
            if (installment.getInvoices().getCreditCard() != null) {
                response.setCreditCardId(installment.getInvoices().getCreditCard().getId());
                response.setAccountName(installment.getInvoices().getCreditCard().getName() + " (Fatura)");
                if (installment.getInvoices().getCreditCard().getAccounts() != null) {
                    response.setAccountId(installment.getInvoices().getCreditCard().getAccounts().getId());
                }
            }
        }
        if (purchase != null) {
            if (purchase.getCategory() != null) {
                response.setCategoryId(purchase.getCategory().getId());
                response.setCategoryName(purchase.getCategory().getName());
            }
            if (purchase.getVehicle() != null) {
                response.setVehicleId(purchase.getVehicle().getId());
                response.setVehicleName(purchase.getVehicle().getName());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public void softDelete(UUID id, OperationScope operationScope) {
        OperationScope scope = normalizeScope(operationScope);
        Optional<InstallmentPlan> instOpt = installmentPlanService.findById(id);

        if (instOpt.isPresent()) {
            InstallmentPlan inst = instOpt.get();
            Users currentUser = SecurityContextUtils.getCurrentUser();
            if (inst.getUser() == null || !inst.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
            }
            deleteScopedInstallments(inst, scope, DateUtils.getEpochNow());
            return;
        }

        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (isTransferSide(transaction)) {
            deleteTransferPair(transaction, scope, dateNow, currentUser);
            return;
        }

        if (transaction.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        if (scope == OperationScope.FROM_THIS_FORWARD) {
            if (transaction.getRecurrenceRule() != null) {
                RecurrenceRule rule = transaction.getRecurrenceRule();
                rule.setStatus(RuleStatus.CANCELED);
                rule.setUpdatedAt(dateNow);
                recurrenceRuleService.save(rule);

                List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(rule.getId(), dateNow);
                for (Transactions tx : futureUnpaidTx) {
                    tx.setDeletedAt(dateNow);
                    if (tx.getAccount().getType() == AccountType.CREDIT_CARD) {
                        deleteInstallmentsAndRestoreInvoice(tx.getId(), dateNow);
                    }
                }
                repository.saveAll(futureUnpaidTx);
            } else {
                repository.deleteByParentId(transaction.getId(), dateNow);
            }
        }

        if (transaction.getAccount().getType() == AccountType.CREDIT_CARD) {
            deleteInstallmentsAndRestoreInvoice(transaction.getId(), dateNow);
            CreditCard card = creditCardService.findByAccountId(transaction.getAccount().getId());
            card.restoreLimit(transaction.getAmount());
            creditCardService.updateLimit(card);
        }

        if (transaction.getPaid() && transaction.getAccount().getType() != AccountType.CREDIT_CARD) {
            Accounts acc = transaction.getAccount();
            if (transaction.getType() == TransactionType.DESPESA) acc.credit(transaction.getAmount());
            else if (transaction.getType() == TransactionType.RECEITA) acc.debit(transaction.getAmount());
            accountsService.update(acc);
        }

        transaction.setDeletedAt(dateNow);
        repository.save(transaction);

        if (transaction.getVehicle() != null && transaction.getCurrentOdometer() != null) {
            recalculateVehicleCurrentOdometer(transaction.getVehicle());
        }
    }

    private void deleteScopedInstallments(InstallmentPlan reference, OperationScope scope, long dateNow) {
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(reference.getPurchaseId()).stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .sorted((a, b) -> a.getCurrentInstallment().compareTo(b.getCurrentInstallment()))
                .toList();
        if (installments.isEmpty()) {
            installments = List.of(reference);
        }
        List<InstallmentPlan> scopedInstallments = selectInstallmentsForScope(installments, scope, reference);
        validateEditableInstallments(scopedInstallments);

        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();
        Map<UUID, CreditCard> cardsToUpdate = new HashMap<>();
        for (InstallmentPlan inst : scopedInstallments) {
            inst.setDeletedAt(dateNow);
            inst.setUpdatedAt(dateNow);
            applyInvoiceDelta(invoicesToUpdate, inst.getInvoices(), inst.getAmount().negate(), dateNow);
            if (inst.getInvoices().getCreditCard() != null && inst.getAmount() != null && inst.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                CreditCard card = inst.getInvoices().getCreditCard();
                card.restoreLimit(inst.getAmount());
                cardsToUpdate.put(card.getId(), card);
            }
        }

        installmentPlanService.saveAll(scopedInstallments);
        invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        cardsToUpdate.values().forEach(creditCardService::updateLimit);

        BigDecimal remainingTotal = installments.stream()
                .filter(inst -> inst.getDeletedAt() == null)
                .map(InstallmentPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        repository.findById(reference.getPurchaseId()).ifPresent(purchase -> {
            purchase.setAmount(remainingTotal);
            purchase.setUpdatedAt(dateNow);
            if (scope == OperationScope.ALL || remainingTotal.compareTo(BigDecimal.ZERO) == 0) {
                purchase.setDeletedAt(dateNow);
            }
            repository.save(purchase);
        });
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
    @Transactional
    public Transactions updateTransaction(UUID id, TransactionDTO dto, OperationScope operationScope) {
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        Accounts oldAccount = transaction.getAccount();
        BigDecimal oldAmount = transaction.getAmount();
        boolean wasPaid = transaction.getPaid();
        TransactionType oldType = transaction.getType();

        if (wasPaid && oldAccount.getType() != AccountType.CREDIT_CARD) {
            if (oldType == TransactionType.DESPESA) oldAccount.credit(oldAmount);
            else if (oldType == TransactionType.RECEITA) oldAccount.debit(oldAmount);
            accountsService.update(oldAccount);
        }

        if (dto.getName() != null) transaction.setName(dto.getName());
        if (dto.getDescription() != null) transaction.setDescription(dto.getDescription());
        if (dto.getType() != null) transaction.setType(dto.getType());

        if (dto.getAmount() != null) {
            transaction.setAmount(dto.getAmount());
        }

        if (dto.getDate() != null) transaction.setDate(dto.getDate());
        if (dto.getPaid() != null) transaction.setPaid(dto.getPaid());

        // Controle para saber se precisamos projetar no final da função
        boolean transformToFixed = false;

        if (dto.getIsFixed() != null) {
            boolean wasFixed = transaction.getFixed() != null ? transaction.getFixed() : false;
            boolean isFixedNow = dto.getIsFixed();

            transaction.setFixed(isFixedNow);

            // Cenário A: Transformou uma transação normal em fixa!
            if (!wasFixed && isFixedNow && dto.getRecurrenceFrequency() != null) {
                RecurrenceRule rule = helper.createRecurrenceRule(dto, transaction.getType(), dateNow, currentUser, oldAccount, null, transaction.getCategory());
                transaction.setRecurrenceRule(rule);
                transformToFixed = true; // Avisa o final do método para projetar
            }

            // Cenário B: Era fixa e o usuário desmarcou (Cancela as futuras)
            if (wasFixed && !isFixedNow && transaction.getRecurrenceRule() != null) {
                RecurrenceRule rule = transaction.getRecurrenceRule();
                rule.setStatus(RuleStatus.CANCELED);
                rule.setUpdatedAt(dateNow);
                recurrenceRuleService.save(rule);

                List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(rule.getId(), dateNow);
                for (Transactions tx : futureUnpaidTx) {
                    tx.setDeletedAt(dateNow);
                }
                repository.saveAll(futureUnpaidTx);
                transaction.setRecurrenceRule(null);
            }
        }

        Accounts currentAccount = oldAccount;
        if (dto.getAccountId() != null && !dto.getAccountId().equals(oldAccount.getId())) {
            currentAccount = accountsService.findByIdOrThrow(dto.getAccountId());
            if (!currentAccount.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
            }
            transaction.setAccount(currentAccount);
        }

        Category currentCategory = transaction.getCategory();
        if (dto.getCategoryId() != null) {
            currentCategory = categoryService.findByIdOrThrow(dto.getCategoryId());
        }
        validateCategoryForTransaction(currentCategory);
        transaction.setCategory(currentCategory);

        if (operationScope == OperationScope.FROM_THIS_FORWARD && transaction.getRecurrenceRule() != null) {
            splitRecurringRuleFromTransactionForward(transaction, dto, dateNow, currentUser, currentAccount);
        }

        boolean shouldRecalculateVehicleOdometer = applyVehicleFieldsOnUpdate(transaction, dto, currentUser);

        if (transaction.getPaid() && currentAccount.getType() != AccountType.CREDIT_CARD) {
            if (transaction.getType() == TransactionType.DESPESA) currentAccount.debit(transaction.getAmount());
            else if (transaction.getType() == TransactionType.RECEITA) currentAccount.credit(transaction.getAmount());
            accountsService.update(currentAccount);
        }

        transaction.setUpdatedAt(dateNow);

        // 1. SALVA A TRANSAÇÃO ATUAL NO BANCO COM A REGRA ANEXADA
        transaction = repository.save(transaction);
        final Transactions savedTransaction = transaction;

        if (shouldRecalculateVehicleOdometer && savedTransaction.getVehicle() != null) {
            recalculateVehicleCurrentOdometer(savedTransaction.getVehicle());
        }

        if (transaction.getGasStation() != null) {
            CompletableFuture.runAsync(() -> gasStationRankingService.updateRanking(savedTransaction));
        }

        // 2. AGORA SIM GERA AS PROJEÇÕES (O banco já consegue enxergar a transação do passo 1)
        if (transformToFixed) {
            LocalDate limiteProjecao = LocalDate.now(DateUtils.zoneId).plusYears(1);
            generateProjectionsForRule(transaction.getRecurrenceRule(), limiteProjecao);
        }

        return transaction;
    }

    private boolean applyVehicleFieldsOnUpdate(Transactions transaction, TransactionDTO dto, Users currentUser) {
        Vehicle vehicle = transaction.getVehicle();
        if (dto.getVehicleId() != null) {
            vehicle = vehicleService.findById(dto.getVehicleId());
            if (!vehicle.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
            }
            transaction.setVehicle(vehicle);
        }

        if (vehicle == null) {
            return false;
        }

        boolean shouldRecalculateVehicleOdometer = false;
        if (dto.getCurrentOdometer() != null) {
            validateOdometerTimelineOnUpdate(transaction, vehicle, dto.getCurrentOdometer());
            shouldRecalculateVehicleOdometer = transaction.getCurrentOdometer() == null
                    || dto.getCurrentOdometer().compareTo(transaction.getCurrentOdometer()) != 0;
            if (shouldRecalculateVehicleOdometer) {
                transaction.setCurrentOdometer(dto.getCurrentOdometer());
            }
        }
        if (dto.getLiters() != null) {
            transaction.setLiters(dto.getLiters());
        }
        if (dto.getFuelType() != null) {
            transaction.setFuelType(dto.getFuelType());
        }
        if (dto.getDrivingPredominance() != null) {
            transaction.setDrivingPredominance(dto.getDrivingPredominance());
        }
        if (dto.getEfficiency() != null) {
            transaction.setEfficiency(dto.getEfficiency());
        }
        return shouldRecalculateVehicleOdometer;
    }

    private void splitRecurringRuleFromTransactionForward(Transactions transaction, TransactionDTO dto, long dateNow, Users currentUser, Accounts account) {
        RecurrenceRule oldRule = transaction.getRecurrenceRule();
        if (oldRule == null) {
            return;
        }

        Long previousEnd = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(transaction.getDate()).minusDays(1));
        oldRule.setEndDate(previousEnd);
        oldRule.setUpdatedAt(dateNow);
        recurrenceRuleService.save(oldRule);

        RecurrenceRule newRule = RecurrenceRule.builder()
                .id(ID.generate())
                .name(dto.getName() != null ? dto.getName() : transaction.getName())
                .description(dto.getDescription() != null ? dto.getDescription() : transaction.getDescription())
                .baseAmount(dto.getAmount() != null ? dto.getAmount() : transaction.getAmount())
                .type(dto.getType() != null ? dto.getType() : transaction.getType())
                .frequency(dto.getRecurrenceFrequency() != null ? dto.getRecurrenceFrequency() : oldRule.getFrequency())
                .startDate(transaction.getDate())
                .endDate(dto.getRecurrenceEndDate() != null ? dto.getRecurrenceEndDate() : oldRule.getEndDate())
                .status(RuleStatus.ACTIVE)
                .createdAt(dateNow)
                .user(currentUser)
                .category(transaction.getCategory())
                .account(account)
                .targetAccount(oldRule.getTargetAccount())
                .build();
        newRule = recurrenceRuleService.save(newRule);
        transaction.setRecurrenceRule(newRule);

        List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(oldRule.getId(), transaction.getDate());
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();

        for (Transactions tx : futureUnpaidTx) {
            tx.setRecurrenceRule(newRule);
            if (dto.getName() != null) tx.setName(dto.getName());
            if (dto.getDescription() != null) tx.setDescription(dto.getDescription());
            if (dto.getType() != null) tx.setType(dto.getType());
            if (dto.getAmount() != null) tx.setAmount(dto.getAmount());
            tx.setUpdatedAt(dateNow);

            if (tx.getAccount().getType() == AccountType.CREDIT_CARD) {
                updateFutureCreditCardOccurrenceInstallments(tx, dto, invoicesToUpdate, dateNow);
            }
        }
        if (!futureUnpaidTx.isEmpty()) {
            repository.saveAll(futureUnpaidTx);
        }
        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
    }

    private void updateFutureCreditCardOccurrenceInstallments(Transactions tx, TransactionDTO dto, Map<UUID, Invoices> invoicesToUpdate, long dateNow) {
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(tx.getId());
        List<InstallmentPlan> changed = new ArrayList<>();
        for (InstallmentPlan inst : installments) {
            if (inst.getDeletedAt() != null || isPaidInvoiceInstallment(inst)) {
                continue;
            }
            BigDecimal oldAmount = inst.getAmount();
            boolean shouldSave = false;
            if (dto.getAmount() != null && dto.getAmount().compareTo(oldAmount) != 0) {
                inst.setAmount(dto.getAmount());
                applyInvoiceDelta(invoicesToUpdate, inst.getInvoices(), dto.getAmount().subtract(oldAmount), dateNow);
                shouldSave = true;
            }
            if (dto.getName() != null) {
                inst.setName(buildInstallmentName(dto.getName(), inst));
                shouldSave = true;
            }
            if (dto.getDescription() != null) {
                inst.setDescription(dto.getDescription());
                shouldSave = true;
            }
            if (dto.getType() != null) {
                inst.setType(dto.getType().name());
                shouldSave = true;
            }
            if (shouldSave) {
                inst.setUpdatedAt(dateNow);
                changed.add(inst);
            }
        }
        if (!changed.isEmpty()) {
            installmentPlanService.saveAll(changed);
        }
    }

    private void validateOdometerTimelineOnUpdate(Transactions transaction, Vehicle vehicle, BigDecimal newOdometer) {
        OdometerValidator.validateValue(newOdometer);
        long createdAt = transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L;
        Long date = transaction.getDate();
        if (date == null) {
            return;
        }

        Optional<Transactions> previousOpt = repository
                .findPreviousOdometerTransactions(vehicle.getId(), transaction.getId(), date, createdAt)
                .stream()
                .findFirst();
        BigDecimal previousOdometer = previousOpt
                .map(Transactions::getCurrentOdometer)
                .orElse(vehicle.getInitialOdometer());

        if (previousOpt.isPresent()
                && previousOpt.get().getCurrentOdometer() != null
                && newOdometer.compareTo(previousOpt.get().getCurrentOdometer()) < 0) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "Odômetro não pode ser menor que o lançamento anterior do veículo."
            );
        }
        OdometerValidator.validateJump(previousOdometer, newOdometer);

        Optional<Transactions> nextOpt = repository
                .findNextOdometerTransactions(vehicle.getId(), transaction.getId(), date, createdAt)
                .stream()
                .findFirst();
        if (nextOpt.isPresent()
                && nextOpt.get().getCurrentOdometer() != null
                && newOdometer.compareTo(nextOpt.get().getCurrentOdometer()) > 0) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "Odômetro não pode ser maior que o próximo lançamento do veículo."
            );
        }
    }

    private void validateVehicleOdometerOnCreate(TransactionDTO dto, Users user) {
        if (dto.getVehicleId() == null || dto.getCurrentOdometer() == null) {
            return;
        }

        Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        OdometerValidator.validateValue(dto.getCurrentOdometer());
        if (dto.getCurrentOdometer().compareTo(vehicle.getCurrentOdometer()) < 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Odômetro não pode ser menor que o odômetro atual do veículo.");
        }
        OdometerValidator.validateJump(vehicle.getCurrentOdometer(), dto.getCurrentOdometer());
    }

    private void validateCategoryForTransaction(Category category) {
        if (category == null || Boolean.TRUE.equals(category.getIsSubCategory())) {
            return;
        }
        if (isVehicleParentCategory(category)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Selecione uma subcategoria de veículo.");
        }
    }

    private boolean isVehicleParentCategory(Category category) {
        if (category.getName() == null) {
            return false;
        }
        String normalizedName = category.getName().trim().toLowerCase(Locale.ROOT);
        return normalizedName.equals("veículo") || normalizedName.equals("veiculos") || normalizedName.equals("veículos");
    }

    private void recalculateVehicleCurrentOdometer(Vehicle vehicle) {
        BigDecimal maxTransactionOdometer = repository.findMaxCurrentOdometerByVehicleId(vehicle.getId());
        BigDecimal maxLogOdometer = vehicleLogRepository.findMaxOdometerReadingByVehicleId(vehicle.getId());
        BigDecimal recalculatedOdometer = vehicle.getInitialOdometer() != null
                ? vehicle.getInitialOdometer()
                : BigDecimal.ZERO;

        if (maxTransactionOdometer != null && maxTransactionOdometer.compareTo(recalculatedOdometer) > 0) {
            recalculatedOdometer = maxTransactionOdometer;
        }
        if (maxLogOdometer != null && maxLogOdometer.compareTo(recalculatedOdometer) > 0) {
            recalculatedOdometer = maxLogOdometer;
        }

        vehicleService.setCurrentOdometer(vehicle, recalculatedOdometer);
    }

    @Override
    @Transactional
    public void cascadeRuleUpdate(UUID ruleId, BigDecimal newAmount) {
        RecurrenceRule rule = recurrenceRuleService.findByIdOrThrow(ruleId);
        BigDecimal oldAmount = rule.getBaseAmount();
        BigDecimal difference = newAmount.subtract(oldAmount);

        rule.setBaseAmount(newAmount);
        rule.setUpdatedAt(DateUtils.getEpochNow());
        recurrenceRuleService.save(rule);

        List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(ruleId, DateUtils.getEpochNow());
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();

        for (Transactions tx : futureUnpaidTx) {
            tx.setAmount(newAmount);
            tx.setUpdatedAt(DateUtils.getEpochNow());

            if (tx.getAccount().getType() == AccountType.CREDIT_CARD) {
                List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(tx.getId());
                for (InstallmentPlan inst : installments) {
                    if (inst.getDeletedAt() != null || isPaidInvoiceInstallment(inst)) {
                        continue;
                    }
                    inst.setAmount(newAmount);
                    inst.setUpdatedAt(DateUtils.getEpochNow());

                    Invoices invoice = invoicesToUpdate.getOrDefault(inst.getInvoices().getId(), inst.getInvoices());
                    invoice.setAmount(invoice.getAmount().add(difference));
                    invoicesToUpdate.put(invoice.getId(), invoice);
                }
                installmentPlanService.saveAll(installments);
            }
        }
        repository.saveAll(futureUnpaidTx);
        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
    }

    @Override
    @Transactional
    public void generateProjectionsByRuleId(UUID ruleId, LocalDate limitDate) {
        RecurrenceRule rule = recurrenceRuleService.findByIdOrThrow(ruleId);
        generateProjectionsForRule(rule, limitDate);
    }

    @Override
    @Transactional
    public void generateProjectionsForRule(RecurrenceRule rule, LocalDate limitDate) {
        Long maxDateEpoch = repository.findMaxDateByRuleId(rule.getId());
        LocalDate nextDate = (maxDateEpoch != null) ? calculateNextDate(DateUtils.epochToLocalDate(maxDateEpoch), rule.getFrequency()) : DateUtils.epochToLocalDate(rule.getStartDate());
        long endDateEpoch = rule.getEndDate() != null ? rule.getEndDate() : Long.MAX_VALUE;

        while (!nextDate.isAfter(limitDate) && DateUtils.localDateToEpoch(nextDate) <= endDateEpoch) {
            long epochNextDate = DateUtils.localDateToEpoch(nextDate);
            if (rule.getType() == TransactionType.TRANSFERENCIA) {
                Transactions transferOut = createTransactionFromRule(rule, TransactionType.TRANSFERENCIA_SAIDA, epochNextDate, rule.getAccount());
                Transactions transferIn = createTransactionFromRule(rule, TransactionType.TRANSFERENCIA_ENTRADA, epochNextDate, rule.getTargetAccount());
                transferIn.setParentTransaction(transferOut);
                repository.saveAll(List.of(transferOut, transferIn));
            } else if (rule.getAccount().getType() == AccountType.CREDIT_CARD) {
                createProjectedCreditCardExpense(rule, epochNextDate);
            } else {
                Transactions tx = createTransactionFromRule(rule, rule.getType(), epochNextDate, rule.getAccount());
                repository.save(tx);
            }
            nextDate = calculateNextDate(nextDate, rule.getFrequency());
        }
    }

    @Override
    @Transactional
    public void adjustBalance(UUID accountId, BigDecimal newBalance) {
        Accounts account = accountsService.findByIdOrThrow(accountId);
        BigDecimal currentBalance = account.getCurrentBalance();
        BigDecimal difference = newBalance.subtract(currentBalance);

        if (difference.compareTo(BigDecimal.ZERO) == 0) return;

        TransactionType type = difference.compareTo(BigDecimal.ZERO) > 0
                ? TransactionType.RECEITA
                : TransactionType.DESPESA;

        String categoryName = "Reajuste de Saldo";

        Category category = categoryService.findCategoryByUserAndName(account.getUser(), categoryName);

        TransactionDTO txDto = new TransactionDTO();
        txDto.setName(categoryName);
        txDto.setAmount(difference.abs());
        txDto.setType(type);
        txDto.setDate(DateUtils.getEpochNow());
        txDto.setAccountId(accountId);
        txDto.setCategoryId(category.getId());
        txDto.setPaid(true);
        txDto.setIsFixed(false);

        this.createTransaction(txDto);
    }

    private void deleteInstallmentsAndRestoreInvoice(UUID purchaseId, long dateNow) {
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(purchaseId);
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();

        for (InstallmentPlan inst : installments) {
            if (inst.getDeletedAt() == null) {
                inst.setDeletedAt(dateNow);
                Invoices invoice = invoicesToUpdate.getOrDefault(inst.getInvoices().getId(), inst.getInvoices());
                invoice.setAmount(invoice.getAmount().subtract(inst.getAmount()));
                invoicesToUpdate.put(invoice.getId(), invoice);
            }
        }
        installmentPlanService.saveAll(installments);
        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
    }

    @Transactional
    protected Transactions updateTransferPair(Transactions current, TransactionDTO dto, OperationScope operationScope) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        TransferPair pair = findTransferPair(current);
        validateTransferPairOwner(pair, currentUser);

        long dateNow = DateUtils.getEpochNow();
        Transactions transferOut = pair.out();
        Transactions transferIn = pair.in();

        Accounts oldOrigin = transferOut.getAccount();
        Accounts oldDest = transferIn.getAccount();

        if (Boolean.TRUE.equals(transferOut.getPaid())) {
            oldOrigin.credit(transferOut.getAmount());
        }
        if (Boolean.TRUE.equals(transferIn.getPaid())) {
            oldDest.debit(transferIn.getAmount());
        }

        Accounts newOrigin = oldOrigin;
        if (dto.getAccountId() != null && !dto.getAccountId().equals(oldOrigin.getId())) {
            newOrigin = accountsService.findByIdOrThrow(dto.getAccountId());
            if (!newOrigin.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
            }
        }

        Accounts newDest = oldDest;
        if (dto.getTargetAccountId() != null && !dto.getTargetAccountId().equals(oldDest.getId())) {
            newDest = accountsService.findByIdOrThrow(dto.getTargetAccountId());
            if (!newDest.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
            }
        }
        validateTransferableAccount(newOrigin);
        validateTransferableAccount(newDest);

        BigDecimal amount = dto.getAmount() != null ? dto.getAmount() : transferOut.getAmount();
        Long date = dto.getDate() != null ? dto.getDate() : transferOut.getDate();
        Boolean paid = dto.getPaid() != null ? dto.getPaid() : transferOut.getPaid();
        String name = dto.getName() != null ? dto.getName() : transferOut.getName();
        String description = dto.getDescription() != null ? dto.getDescription() : transferOut.getDescription();

        Category category = transferOut.getCategory();
        if (dto.getCategoryId() != null) {
            category = categoryService.findByIdOrThrow(dto.getCategoryId());
        }

        transferOut.setName(name);
        transferOut.setDescription(description);
        transferOut.setAmount(amount);
        transferOut.setDate(date);
        transferOut.setPaid(paid);
        transferOut.setAccount(newOrigin);
        transferOut.setCategory(category);
        transferOut.setType(TransactionType.TRANSFERENCIA_SAIDA);
        transferOut.setUpdatedAt(dateNow);

        transferIn.setName(name);
        transferIn.setDescription(description);
        transferIn.setAmount(amount);
        transferIn.setDate(date);
        transferIn.setPaid(paid);
        transferIn.setAccount(newDest);
        transferIn.setCategory(category);
        transferIn.setType(TransactionType.TRANSFERENCIA_ENTRADA);
        transferIn.setParentTransaction(transferOut);
        transferIn.setUpdatedAt(dateNow);

        if (Boolean.TRUE.equals(paid)) {
            newOrigin.debit(amount);
            newDest.credit(amount);
        }

        List<Accounts> accountsToUpdate = new ArrayList<>();
        addUniqueAccount(accountsToUpdate, oldOrigin);
        addUniqueAccount(accountsToUpdate, oldDest);
        addUniqueAccount(accountsToUpdate, newOrigin);
        addUniqueAccount(accountsToUpdate, newDest);
        accountsToUpdate.forEach(accountsService::update);

        if (operationScope == OperationScope.FROM_THIS_FORWARD && transferOut.getRecurrenceRule() != null) {
            cascadeRuleUpdate(transferOut.getRecurrenceRule().getId(), amount);
        }

        repository.saveAll(List.of(transferOut, transferIn));
        return transferOut;
    }

    private void deleteTransferPair(Transactions current, OperationScope operationScope, long dateNow, Users currentUser) {
        TransferPair pair = findTransferPair(current);
        validateTransferPairOwner(pair, currentUser);

        Transactions transferOut = pair.out();
        Transactions transferIn = pair.in();

        if (operationScope == OperationScope.FROM_THIS_FORWARD && transferOut.getRecurrenceRule() != null) {
            RecurrenceRule rule = transferOut.getRecurrenceRule();
            rule.setStatus(RuleStatus.CANCELED);
            rule.setUpdatedAt(dateNow);
            recurrenceRuleService.save(rule);

            List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(rule.getId(), dateNow);
            for (Transactions tx : futureUnpaidTx) {
                tx.setDeletedAt(dateNow);
            }
            repository.saveAll(futureUnpaidTx);
        }

        if (Boolean.TRUE.equals(transferOut.getPaid())) {
            Accounts origin = transferOut.getAccount();
            origin.credit(transferOut.getAmount());
            accountsService.update(origin);
        }
        if (Boolean.TRUE.equals(transferIn.getPaid())) {
            Accounts dest = transferIn.getAccount();
            dest.debit(transferIn.getAmount());
            accountsService.update(dest);
        }

        transferOut.setDeletedAt(dateNow);
        transferIn.setDeletedAt(dateNow);
        repository.saveAll(List.of(transferOut, transferIn));
    }

    private TransferPair findTransferPair(Transactions current) {
        if (current.getType() == TransactionType.TRANSFERENCIA_ENTRADA) {
            Transactions parent = current.getParentTransaction();
            if (parent == null) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Transferência de entrada sem vínculo com a saída.");
            }
            return new TransferPair(parent, current);
        }

        if (current.getType() == TransactionType.TRANSFERENCIA_SAIDA) {
            Transactions child = repository.findTransferChildByParentId(current.getId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, "Transferência de saída sem lançamento de entrada vinculado."));
            return new TransferPair(current, child);
        }

        throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Transação não é uma transferência vinculada.");
    }

    private void validateTransferPairOwner(TransferPair pair, Users currentUser) {
        if (!pair.out().getUser().getId().equals(currentUser.getId()) || !pair.in().getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }
    }

    private boolean isTransferSide(Transactions transaction) {
        return transaction.getType() == TransactionType.TRANSFERENCIA_SAIDA
                || transaction.getType() == TransactionType.TRANSFERENCIA_ENTRADA;
    }

    private void addUniqueAccount(List<Accounts> accounts, Accounts account) {
        if (account == null) return;
        boolean alreadyAdded = accounts.stream().anyMatch(item -> item.getId().equals(account.getId()));
        if (!alreadyAdded) {
            accounts.add(account);
        }
    }

    /**
     * Garante que edição de transferência continue restrita a contas transacionais.
     */
    private void validateTransferableAccount(Accounts account) {
        if (account.getType() == AccountType.CREDIT_CARD || account.getType() == AccountType.INVESTMENT) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSFER_ACCOUNT_NOT_VALID);
        }
    }

    private record TransferPair(Transactions out, Transactions in) {
    }

    private void createProjectedCreditCardExpense(RecurrenceRule rule, long epochNextDate) {
        CreditCard card = creditCardService.findByAccountId(rule.getAccount().getId());
        Transactions purchaseTransaction = createTransactionFromRule(rule, rule.getType(), epochNextDate, rule.getAccount());
        purchaseTransaction = repository.save(purchaseTransaction);

        LocalDateTime dataCompra = DateUtils.epochToLocalDateTime(epochNextDate);
        LocalDateTime dataVencimentoFatura = helper.calculateInvoiceDate(dataCompra, card.getCloseDay(), card.getBestDay());
        int invMonth = dataVencimentoFatura.getMonthValue();
        int invYear = dataVencimentoFatura.getYear();

        Invoices invoice = invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invMonth, invYear)
                .orElseGet(() -> invoicesService.save(Invoices.builder()
                        .id(ID.generate()).month(invMonth).year(invYear).amount(BigDecimal.ZERO)
                        .expirationDate(DateUtils.localDateTimeToEpoch(dataVencimentoFatura)).paid(false).enabled(true)
                        .createdAt(DateUtils.getEpochNow()).creditCard(card).user(rule.getUser()).build()));

        invoice.setAmount(invoice.getAmount().add(rule.getBaseAmount()));
        invoicesService.save(invoice);

        InstallmentPlan installment = InstallmentPlan.builder()
                .id(ID.generate()).name(rule.getName()).type(TransactionType.DESPESA.name()).amount(rule.getBaseAmount())
                .totalInstallmentsPlan(1).currentInstallment(1).fixed(true).paid(false).purchaseId(purchaseTransaction.getId())
                .enabled(true).createdAt(DateUtils.getEpochNow()).date(invoice.getExpirationDate()).invoices(invoice).user(rule.getUser()).build();
        installmentPlanService.save(installment);
    }

    private Transactions createTransactionFromRule(RecurrenceRule rule, TransactionType type, long date, Accounts account) {
        return Transactions.builder().id(ID.generate()).name(rule.getName()).description(rule.getDescription())
                .type(type).amount(rule.getBaseAmount()).date(date).paid(false).fixed(true).enabled(true)
                .createdAt(DateUtils.getEpochNow()).account(account).category(rule.getCategory()).user(rule.getUser()).recurrenceRule(rule).build();
    }

    private LocalDate calculateNextDate(LocalDate current, RecurrenceFrequency freq) {
        return switch (freq) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }
}

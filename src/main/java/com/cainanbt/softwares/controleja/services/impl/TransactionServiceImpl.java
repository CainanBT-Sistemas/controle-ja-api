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
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDateService;
import com.cainanbt.softwares.controleja.services.processors.TransactionHelper;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessor;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessorFactory;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleOdometerTimelineService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleRefuelMetricsService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleTransactionRules;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private static final Pattern INSTALLMENT_SUFFIX = Pattern.compile(".*\\((\\d+)/(\\d+)\\)$");

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final RecurrenceRuleService recurrenceRuleService;
    private final TransactionProcessorFactory processorFactory;
    private final TransactionHelper helper;
    private final VehicleService vehicleService;
    private final VehicleOdometerTimelineService odometerTimelineService;
    private final VehicleRefuelMetricsService refuelMetricsService;
    private final InvoiceDateService invoiceDateService;

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        Accounts account = accountsService.findById(dto.getAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }

        Category category;
        if (dto.getType() == TransactionType.TRANSFERENCIA) {
            category = categoryService.findTransferCategory(user);
        } else if (shouldResolveVehicleTechnicalCategory(dto)) {
            category = categoryService.ensureVehicleEntryCategory(user, VehicleTransactionRules.isRefuel(dto));
        } else {
            category = categoryService.findById(dto.getCategoryId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));
            validateCategoryForTransaction(category);
        }
        validateVehicleOdometerOnCreate(dto, user);

        TransactionProcessor processor = processorFactory.getProcessor(dto, account);

        // Salva a transação atual e a regra de recorrência
        Transactions savedTransaction = processor.process(dto, account, category, user);

        if (VehicleTransactionRules.isRefuel(savedTransaction)) {
            odometerTimelineService.recalculateCurrentOdometer(savedTransaction.getVehicle());
            refuelMetricsService.recalculate(savedTransaction.getVehicle());
        }

        // CORREÇÃO: Roda na mesma thread. É tão rápido (5ms) que não vai travar o celular.
        // Como roda dentro da mesma transação, o banco enxerga a regra que acabou de ser criada!
        if (savedTransaction.getRecurrenceRule() != null && Boolean.TRUE.equals(dto.getIsFixed())) {
            LocalDate limiteProjecao = LocalDate.now(DateUtils.zoneId).plusYears(1);
            generateProjectionsForRule(savedTransaction.getRecurrenceRule(), limiteProjecao);
        }

        return savedTransaction;
    }

    private boolean shouldResolveVehicleTechnicalCategory(TransactionDTO dto) {
        return dto.getType() == TransactionType.DESPESA
                && dto.getVehicleId() != null
                && dto.getCategoryId() == null;
    }

    @Override
    public List<TransactionResponseDTO> listLastTransactionsDTO(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start == null || end == null) return Collections.emptyList();

        List<Transactions> normalTx = repository.findCashFlowTransactionsByMonth(user.getId(), start, end);
        List<TransactionResponseDTO> responseList = new ArrayList<>(normalTx.stream().map(TransactionResponseDTO::toBasicDTO).toList());

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
            return TransactionResponseDTO.toDetailedDTO(updateTransferPair(current, dto, scope));
        }

        List<Transactions> standardInstallmentSeries = findStandardInstallmentSeries(current);
        if (!standardInstallmentSeries.isEmpty() && shouldUseStandardInstallmentSeriesUpdate(current, dto, scope)) {
            return updateStandardInstallmentSeries(current, standardInstallmentSeries, dto, scope);
        }

        Transactions transaction = updateTransaction(id, dto, scope);

        return TransactionResponseDTO.toDetailedDTO(transaction);
    }

    private List<Transactions> findStandardInstallmentSeries(Transactions current) {
        if (current.getAccount() == null || current.getAccount().getType() == AccountType.CREDIT_CARD) {
            return Collections.emptyList();
        }
        if (current.getType() != TransactionType.DESPESA && current.getType() != TransactionType.RECEITA) {
            return Collections.emptyList();
        }

        Transactions parent = current.getParentTransaction() != null ? current.getParentTransaction() : current;
        List<Transactions> children = repository.findByParentTransactionId(parent.getId());
        if (children.isEmpty() && current.getParentTransaction() == null) {
            return Collections.emptyList();
        }

        List<Transactions> series = new ArrayList<>(children.size() + 1);
        if (parent.getDeletedAt() == null) {
            series.add(parent);
        }
        series.addAll(children.stream()
                .filter(tx -> tx.getDeletedAt() == null)
                .toList());

        return series.stream()
                .sorted((left, right) -> Integer.compare(
                        resolveInstallmentNumber(left),
                        resolveInstallmentNumber(right)))
                .toList();
    }

    private boolean shouldUseStandardInstallmentSeriesUpdate(Transactions current, TransactionDTO dto, OperationScope scope) {
        return scope == OperationScope.ALL
                || hasStandardInstallmentPaidChange(current, dto)
                || hasStandardInstallmentNonPaidChange(current, dto);
    }

    private boolean hasStandardInstallmentPaidChange(Transactions current, TransactionDTO dto) {
        return dto.getPaid() != null && !Objects.equals(dto.getPaid(), current.getPaid());
    }

    private boolean hasStandardInstallmentNonPaidChange(Transactions current, TransactionDTO dto) {
        return (dto.getDate() != null && !Objects.equals(dto.getDate(), current.getDate()))
                || (dto.getName() != null && !Objects.equals(removeInstallmentSuffix(dto.getName()), removeInstallmentSuffix(current.getName())))
                || (dto.getDescription() != null && !Objects.equals(normalizeText(dto.getDescription()), normalizeText(current.getDescription())))
                || (dto.getAmount() != null && current.getAmount() != null && dto.getAmount().compareTo(current.getAmount()) != 0)
                || (dto.getType() != null && dto.getType() != current.getType())
                || (dto.getAccountId() != null && (current.getAccount() == null || !dto.getAccountId().equals(current.getAccount().getId())))
                || (dto.getCategoryId() != null && (current.getCategory() == null || !dto.getCategoryId().equals(current.getCategory().getId())));
    }

    private TransactionResponseDTO updateStandardInstallmentSeries(
            Transactions reference,
            List<Transactions> series,
            TransactionDTO dto,
            OperationScope scope) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        for (Transactions tx : series) {
            validateTransactionOwner(tx, currentUser);
        }

        boolean paidChanged = hasStandardInstallmentPaidChange(reference, dto);
        boolean nonPaidChanged = hasStandardInstallmentNonPaidChange(reference, dto);
        validateSupportedStandardInstallmentSeriesUpdate(reference, dto, scope, paidChanged, nonPaidChanged);

        List<Transactions> scoped = paidChanged
                ? selectStandardInstallmentsForScope(series, OperationScope.ONLY_THIS, reference)
                : selectStandardInstallmentsForScope(series, scope, reference);
        validateEditableStandardInstallmentScope(scoped, reference, paidChanged);
        String baseName = removeInstallmentSuffix(dto.getName() != null ? dto.getName() : reference.getName());
        int totalInstallments = series.size();
        Integer referenceInstallment = resolveInstallmentNumber(reference);
        Integer targetDay = dto.getDate() != null ? DateUtils.epochToLocalDate(dto.getDate()).getDayOfMonth() : null;

        for (Transactions tx : scoped) {
            Accounts oldAccount = tx.getAccount();
            BigDecimal oldAmount = tx.getAmount();
            TransactionType oldType = tx.getType();
            boolean wasPaid = Boolean.TRUE.equals(tx.getPaid());

            if (dto.getName() != null) {
                tx.setName(buildInstallmentName(baseName, resolveInstallmentNumber(tx), totalInstallments));
            }
            if (dto.getDescription() != null) {
                tx.setDescription(dto.getDescription());
            }
            if (dto.getDate() != null) {
                int monthsToAdd = resolveInstallmentNumber(tx) - referenceInstallment;
                tx.setDate(recalculateInstallmentDate(dto.getDate(), monthsToAdd, targetDay));
            }
            if (paidChanged) {
                tx.setPaid(dto.getPaid());
            }

            if (paidChanged && !Objects.equals(wasPaid, Boolean.TRUE.equals(tx.getPaid()))) {
                if (wasPaid) {
                    reverseTransactionBalance(oldAccount, oldType, oldAmount);
                }
                if (Boolean.TRUE.equals(tx.getPaid())) {
                    applyTransactionBalance(tx.getAccount(), tx.getType(), tx.getAmount());
                }
            }
            tx.setUpdatedAt(dateNow);
        }

        repository.saveAll(scoped);
        return TransactionResponseDTO.toDetailedDTO(reference);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateSupportedStandardInstallmentSeriesUpdate(
            Transactions reference,
            TransactionDTO dto,
            OperationScope scope,
            boolean paidChanged,
            boolean nonPaidChanged) {
        if (scope == OperationScope.ALL) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração em todas as parcelas comuns não está disponível. Use somente esta parcela ou esta e as próximas.");
        }
        if (paidChanged && nonPaidChanged) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Altere o status de pagamento separadamente dos demais campos da parcela.");
        }
        if (paidChanged) {
            return;
        }
        if (dto.getPaid() != null) {
            dto.setPaid(reference.getPaid());
        }
        if (scope == OperationScope.ONLY_THIS) {
            return;
        }
        if (dto.getAmount() != null && reference.getAmount() != null && dto.getAmount().compareTo(reference.getAmount()) != 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração em massa de valor de parcelas comuns não está disponível neste momento.");
        }
        if (dto.getType() != null && dto.getType() != reference.getType()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração em massa de tipo de parcelas comuns não está disponível neste momento.");
        }
        if (dto.getAccountId() != null && (reference.getAccount() == null || !dto.getAccountId().equals(reference.getAccount().getId()))) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração em massa de conta de parcelas comuns não está disponível neste momento.");
        }
        if (dto.getCategoryId() != null && (reference.getCategory() == null || !dto.getCategoryId().equals(reference.getCategory().getId()))) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração em massa de categoria de parcelas comuns não está disponível neste momento.");
        }
    }

    private void validateEditableStandardInstallmentScope(List<Transactions> scoped, Transactions reference, boolean paidChanged) {
        if (paidChanged) {
            return;
        }
        for (Transactions tx : scoped) {
            if (resolveInstallmentNumber(tx) < resolveInstallmentNumber(reference)) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração de parcelas anteriores não é permitida.");
            }
            if (Boolean.TRUE.equals(tx.getPaid())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Existem parcelas pagas neste parcelamento. Cancele ou ajuste essas parcelas antes de alterar as próximas.");
            }
        }
    }

    private void reverseTransactionBalance(Accounts account, TransactionType type, BigDecimal amount) {
        if (account == null || account.getType() == AccountType.CREDIT_CARD || amount == null || type == null) {
            return;
        }
        if (type == TransactionType.DESPESA) {
            account.credit(amount);
        } else if (type == TransactionType.RECEITA) {
            account.debit(amount);
        }
        accountsService.update(account);
    }

    private void applyTransactionBalance(Accounts account, TransactionType type, BigDecimal amount) {
        if (account == null || account.getType() == AccountType.CREDIT_CARD || amount == null || type == null) {
            return;
        }
        if (type == TransactionType.DESPESA) {
            account.debit(amount);
        } else if (type == TransactionType.RECEITA) {
            account.credit(amount);
        }
        accountsService.update(account);
    }

    private List<Transactions> selectStandardInstallmentsForScope(List<Transactions> series, OperationScope scope, Transactions reference) {
        if (scope == OperationScope.ALL) {
            return series;
        }

        int referenceInstallment = resolveInstallmentNumber(reference);
        if (scope == OperationScope.FROM_THIS_FORWARD) {
            return series.stream()
                    .filter(tx -> resolveInstallmentNumber(tx) >= referenceInstallment)
                    .toList();
        }

        return series.stream()
                .filter(tx -> tx.getId().equals(reference.getId()))
                .toList();
    }

    private int resolveInstallmentNumber(Transactions transaction) {
        String name = transaction.getName();
        if (name != null) {
            Matcher matcher = INSTALLMENT_SUFFIX.matcher(name.trim());
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return transaction.getParentTransaction() == null ? 1 : Integer.MAX_VALUE;
    }

    private long recalculateInstallmentDate(Long referenceDateEpoch, int monthsToAdd, int targetDay) {
        LocalDate targetMonth = DateUtils.epochToLocalDate(referenceDateEpoch).plusMonths(monthsToAdd);
        int safeDay = Math.min(targetDay, targetMonth.lengthOfMonth());
        return DateUtils.localDateToEpoch(LocalDate.of(targetMonth.getYear(), targetMonth.getMonth(), safeDay));
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
        int currentInstallmentCount = activeInstallments.stream()
                .map(InstallmentPlan::getTotalInstallmentsPlan)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(activeInstallments.size());

        if (dto.getInstallments() != null && dto.getInstallments() != currentInstallmentCount) {
            if (scope != OperationScope.ALL) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração da quantidade de parcelas só pode ser aplicada ao lançamento inteiro.");
            }
            List<InstallmentPlan> lockedInstallments = installmentPlanService.findByPurchaseIdForUpdate(purchase.getId());
            Transactions lockedPurchase = purchase;
            int lockedInstallmentCount = lockedInstallments.stream()
                    .filter(inst -> inst.getDeletedAt() == null)
                    .filter(inst -> isCanonicalPurchaseInstallment(inst, lockedPurchase))
                    .map(InstallmentPlan::getTotalInstallmentsPlan)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
            if (lockedInstallmentCount == dto.getInstallments()) {
                return TransactionResponseDTO.toDetailedDTO(purchase);
            }
            return updateCreditCardInstallmentCount(purchase, lockedInstallments, dto, dateNow);
        }

        if (purchase != null
                && purchase.getRecurrenceRule() != null
                && scope == OperationScope.FROM_THIS_FORWARD) {
            return updateRecurringCreditCardPurchases(purchase, dto, dateNow, currentUser);
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
            return TransactionResponseDTO.toDetailedDTO(purchase);
        }

        return buildInstallmentResponse(installmentsToUpdate.stream().findFirst().orElse(installments.get(0)));
    }

    /**
     * Separa a série na ocorrência escolhida e atualiza somente as compras recorrentes editáveis a partir dela.
     */
    private TransactionResponseDTO updateRecurringCreditCardPurchases(
            Transactions selectedPurchase,
            TransactionDTO dto,
            long dateNow,
            Users currentUser) {
        RecurrenceRule oldRule = selectedPurchase.getRecurrenceRule();
        Long selectedOriginalDate = selectedPurchase.getDate();
        Long originalRuleEndDate = oldRule.getEndDate();

        List<Transactions> affectedPurchases = new ArrayList<>(
                repository.findFutureUnpaidByRuleId(oldRule.getId(), selectedOriginalDate)
        );
        if (affectedPurchases.stream().noneMatch(tx -> tx.getId().equals(selectedPurchase.getId()))) {
            affectedPurchases.add(selectedPurchase);
        }
        affectedPurchases = affectedPurchases.stream()
                .filter(tx -> tx.getDeletedAt() == null)
                .filter(tx -> tx.getDate() != null && tx.getDate() >= selectedOriginalDate)
                .sorted((left, right) -> left.getDate().compareTo(right.getDate()))
                .toList();

        Map<UUID, List<InstallmentPlan>> installmentsByPurchase = new HashMap<>();
        for (Transactions purchase : affectedPurchases) {
            validateTransactionOwner(purchase, currentUser);
            List<InstallmentPlan> active = installmentPlanService.findByPurchaseId(purchase.getId()).stream()
                    .filter(installment -> installment.getDeletedAt() == null)
                    .sorted((left, right) -> left.getCurrentInstallment().compareTo(right.getCurrentInstallment()))
                    .toList();
            if (active.isEmpty()) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra recorrente sem parcela ativa para atualizar.");
            }
            validateEditableInstallments(active);
            installmentsByPurchase.put(purchase.getId(), active);
        }

        List<InstallmentPlan> selectedInstallments = installmentsByPurchase.get(selectedPurchase.getId());
        CreditCard targetCard = resolveTargetCard(dto, selectedPurchase, selectedInstallments);
        if (targetCard.getUser() == null || !targetCard.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }
        Category targetCategory = selectedPurchase.getCategory();
        if (dto.getCategoryId() != null) {
            targetCategory = categoryService.findByIdOrThrow(dto.getCategoryId());
            validateCategoryForTransaction(targetCategory);
        }

        Long newSeriesStart = dto.getDate() != null ? dto.getDate() : selectedOriginalDate;
        Long newSeriesEnd = dto.getRecurrenceEndDate() != null ? dto.getRecurrenceEndDate() : originalRuleEndDate;
        if (newSeriesEnd != null && newSeriesEnd < newSeriesStart) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A data final da recorrência deve ser igual ou posterior à nova data da compra.");
        }
        RecurrenceFrequency frequency = dto.getRecurrenceFrequency() != null
                ? dto.getRecurrenceFrequency()
                : oldRule.getFrequency();

        oldRule.setEndDate(DateUtils.localDateToEpoch(
                DateUtils.epochToLocalDate(selectedOriginalDate).minusDays(1)
        ));
        oldRule.setUpdatedAt(dateNow);
        recurrenceRuleService.save(oldRule);

        RecurrenceRule newRule = RecurrenceRule.builder()
                .id(ID.generate())
                .name(dto.getName() != null ? dto.getName() : selectedPurchase.getName())
                .description(dto.getDescription() != null ? dto.getDescription() : selectedPurchase.getDescription())
                .baseAmount(dto.getAmount() != null ? dto.getAmount() : selectedPurchase.getAmount())
                .type(dto.getType() != null ? dto.getType() : selectedPurchase.getType())
                .frequency(frequency)
                .startDate(newSeriesStart)
                .endDate(newSeriesEnd)
                .status(RuleStatus.ACTIVE)
                .createdAt(dateNow)
                .user(currentUser)
                .category(targetCategory)
                .account(targetCard.getAccounts())
                .targetAccount(oldRule.getTargetAccount())
                .build();
        newRule = recurrenceRuleService.save(newRule);

        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();
        List<InstallmentPlan> installmentsToUpdate = new ArrayList<>();
        BigDecimal selectedTotalBefore = selectedInstallments.stream()
                .map(InstallmentPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime occurrenceDate = DateUtils.epochToLocalDateTime(newSeriesStart);

        for (int index = 0; index < affectedPurchases.size(); index++) {
            Transactions purchase = affectedPurchases.get(index);
            if (index > 0) {
                occurrenceDate = calculateNextDate(occurrenceDate.toLocalDate(), frequency).atStartOfDay();
            }

            List<InstallmentPlan> purchaseInstallments = installmentsByPurchase.get(purchase.getId());
            for (InstallmentPlan installment : purchaseInstallments) {
                BigDecimal oldAmount = installment.getAmount();
                if (dto.getAmount() != null && dto.getAmount().compareTo(oldAmount) != 0) {
                    installment.setAmount(dto.getAmount());
                    applyInvoiceDelta(invoicesToUpdate, installment.getInvoices(), dto.getAmount().subtract(oldAmount), dateNow);
                }
                if (dto.getName() != null) {
                    installment.setName(buildInstallmentName(dto.getName(), installment));
                }
                if (dto.getDescription() != null) {
                    installment.setDescription(dto.getDescription());
                }
                if (dto.getType() != null) {
                    installment.setType(dto.getType().name());
                }
                if (dto.getDate() != null || dto.getRecurrenceFrequency() != null || targetCardChanged(targetCard, installment)) {
                    moveInstallmentToInvoice(
                            installment,
                            targetCard,
                            occurrenceDate.plusMonths(installment.getCurrentInstallment() - 1L),
                            invoicesToUpdate,
                            dateNow
                    );
                }
                installment.setFixed(true);
                installment.setUpdatedAt(dateNow);
                installmentsToUpdate.add(installment);
            }

            BigDecimal purchaseTotal = purchaseInstallments.stream()
                    .map(InstallmentPlan::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            purchase.setAmount(purchaseTotal);
            if (dto.getName() != null) purchase.setName(dto.getName());
            if (dto.getDescription() != null) purchase.setDescription(dto.getDescription());
            if (dto.getType() != null) purchase.setType(dto.getType());
            purchase.setDate(DateUtils.localDateTimeToEpoch(occurrenceDate));
            purchase.setCategory(targetCategory);
            purchase.setCreditCard(targetCard);
            purchase.setAccount(targetCard.getAccounts());
            purchase.setFixed(true);
            purchase.setRecurrenceRule(newRule);
            purchase.setUpdatedAt(dateNow);
        }

        installmentPlanService.saveAll(installmentsToUpdate);
        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
        repository.saveAll(affectedPurchases);

        if (dto.getAmount() != null) {
            BigDecimal selectedTotalAfter = selectedInstallments.stream()
                    .map(InstallmentPlan::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            adjustCreditCardLimitForPurchaseAmountChange(targetCard, selectedTotalBefore, selectedTotalAfter);
        }

        log.info(
                "Recurring credit card purchase updated: purchaseId={}, recurrenceRuleId={}, occurrences={}",
                selectedPurchase.getId(),
                newRule.getId(),
                affectedPurchases.size()
        );
        return TransactionResponseDTO.toDetailedDTO(selectedPurchase);
    }

    /**
     * Garante que a compra recorrente pertence ao usuário autenticado antes de alterar a série.
     */
    private void validateTransactionOwner(Transactions transaction, Users currentUser) {
        if (transaction.getUser() == null || !transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }
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

        Transactions purchaseToValidate = purchase;
        List<InstallmentPlan> purchaseAdjustments = installments.stream()
                .filter(inst -> !isCanonicalPurchaseInstallment(inst, purchaseToValidate))
                .toList();
        if (!purchaseAdjustments.isEmpty()) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "Não é possível alterar o parcelamento porque a compra possui desconto, estorno ou ajuste vinculado."
            );
        }

        List<InstallmentPlan> activeInstallments = installments.stream()
                .filter(inst -> isCanonicalPurchaseInstallment(inst, purchaseToValidate))
                .filter(inst -> inst.getDeletedAt() == null)
                .sorted((a, b) -> a.getCurrentInstallment().compareTo(b.getCurrentInstallment()))
                .toList();

        if (activeInstallments.isEmpty()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra sem parcelas ativas para recalcular.");
        }
        if (activeInstallments.stream().anyMatch(this::isPaidInvoiceInstallment)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque a compra possui parcela em fatura paga.");
        }
        int currentInstallmentCount = activeInstallments.stream()
                .map(InstallmentPlan::getTotalInstallmentsPlan)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(activeInstallments.size());
        if (dto.getInstallments() > currentInstallmentCount) {
            return increaseCreditCardInstallmentCount(purchase, installments, activeInstallments, dto, dateNow);
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

        return TransactionResponseDTO.toDetailedDTO(purchase);
    }

    private TransactionResponseDTO increaseCreditCardInstallmentCount(
            Transactions purchase,
            List<InstallmentPlan> allInstallments,
            List<InstallmentPlan> activeInstallments,
            TransactionDTO dto,
            long dateNow) {
        if (purchase.getRecurrenceRule() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento de uma compra recorrente.");
        }
        if (purchase.getDate() == null || purchase.getAmount() == null || purchase.getCreditCard() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra sem dados completos para recalcular o parcelamento.");
        }
        if (dto.getAmount() != null && dto.getAmount().compareTo(purchase.getAmount()) != 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Altere o valor da compra separadamente antes de mudar o parcelamento.");
        }
        if (dto.getDate() != null && !dto.getDate().equals(purchase.getDate())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Altere a data da compra separadamente antes de mudar o parcelamento.");
        }
        if (dto.getCreditCardId() != null && !dto.getCreditCardId().equals(purchase.getCreditCard().getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Altere o cartão da compra separadamente antes de mudar o parcelamento.");
        }
        if (dto.getAccountId() != null
                && purchase.getCreditCard().getAccounts() != null
                && !dto.getAccountId().equals(purchase.getCreditCard().getAccounts().getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Altere o cartão da compra separadamente antes de mudar o parcelamento.");
        }
        if (allInstallments.stream().anyMatch(inst -> inst.getDeletedAt() != null || Boolean.FALSE.equals(inst.getEnabled()))) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque a compra possui parcela removida ou desabilitada.");
        }
        if (allInstallments.size() != activeInstallments.size()) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque a compra possui histórico protegido.");
        }

        BigDecimal currentTotal = activeInstallments.stream()
                .map(InstallmentPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (currentTotal.compareTo(purchase.getAmount()) != 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque o valor da compra já foi ajustado.");
        }

        CreditCard card = purchase.getCreditCard();
        LocalDateTime purchaseDate = DateUtils.epochToLocalDateTime(purchase.getDate());
        Map<Integer, Invoices> targetInvoices = new HashMap<>();
        Map<String, Invoices> existingInvoicesByPeriod = new HashMap<>();

        for (InstallmentPlan installment : activeInstallments) {
            Invoices invoice = installment.getInvoices();
            validateInvoiceForInstallmentIncrease(invoice);
            validateInvoiceFinancialLinks(invoice, purchase, activeInstallments);

            LocalDateTime expectedInvoiceDate = helper.calculateInvoiceDate(
                    purchaseDate.plusMonths(installment.getCurrentInstallment() - 1L),
                    card.getCloseDay(),
                    card.getBestDay()
            );
            if (!sameInvoicePeriod(invoice, expectedInvoiceDate)) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque a compra possui parcela adiantada.");
            }
            existingInvoicesByPeriod.put(invoicePeriodKey(invoice.getMonth(), invoice.getYear()), invoice);
        }

        for (int index = 0; index < dto.getInstallments(); index++) {
            LocalDateTime installmentDate = purchaseDate.plusMonths(index);
            LocalDateTime invoiceDate = helper.calculateInvoiceDate(
                    installmentDate,
                    card.getCloseDay(),
                    card.getBestDay()
            );
            String periodKey = invoicePeriodKey(invoiceDate.getMonthValue(), invoiceDate.getYear());
            Invoices invoice = existingInvoicesByPeriod.get(periodKey);
            if (invoice == null) {
                invoice = invoicesService.findByCreditCardIdAndMonthAndYear(
                        card.getId(),
                        invoiceDate.getMonthValue(),
                        invoiceDate.getYear()
                ).orElse(null);
                if (invoice != null) {
                    validateInvoiceForInstallmentIncrease(invoice);
                    validateInvoiceFinancialLinks(invoice, purchase, activeInstallments);
                    existingInvoicesByPeriod.put(periodKey, invoice);
                }
            }
            if (invoice != null) {
                targetInvoices.put(index + 1, invoice);
            }
        }

        List<BigDecimal> newAmounts = splitInstallmentAmount(purchase.getAmount(), dto.getInstallments());
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();
        for (InstallmentPlan installment : activeInstallments) {
            applyInvoiceDelta(invoicesToUpdate, installment.getInvoices(), installment.getAmount().negate(), dateNow);
        }

        List<InstallmentPlan> installmentsToSave = new ArrayList<>();
        String baseName = dto.getName() != null
                ? dto.getName()
                : removeInstallmentSuffix(activeInstallments.get(0).getName());
        for (int index = 0; index < dto.getInstallments(); index++) {
            int installmentNumber = index + 1;
            Invoices invoice = targetInvoices.get(installmentNumber);
            if (invoice == null) {
                LocalDateTime invoiceDate = helper.calculateInvoiceDate(
                        purchaseDate.plusMonths(index),
                        card.getCloseDay(),
                        card.getBestDay()
                );
                invoice = invoicesService.save(Invoices.builder()
                        .id(ID.generate())
                        .month(invoiceDate.getMonthValue())
                        .year(invoiceDate.getYear())
                        .amount(BigDecimal.ZERO)
                        .expirationDate(DateUtils.localDateTimeToEpoch(invoiceDate))
                        .paid(false)
                        .enabled(true)
                        .createdAt(dateNow)
                        .creditCard(card)
                        .user(purchase.getUser())
                        .build());
                targetInvoices.put(installmentNumber, invoice);
            }

            BigDecimal newAmount = newAmounts.get(index);
            InstallmentPlan installment;
            if (index < activeInstallments.size()) {
                installment = activeInstallments.get(index);
            } else {
                installment = InstallmentPlan.builder()
                        .id(ID.generate())
                        .purchaseId(purchase.getId())
                        .paid(false)
                        .enabled(true)
                        .fixed(Boolean.TRUE.equals(purchase.getFixed()))
                        .createdAt(dateNow)
                        .user(purchase.getUser())
                        .build();
            }
            installment.setName(buildInstallmentName(baseName, installmentNumber, dto.getInstallments()));
            installment.setDescription(dto.getDescription() != null ? dto.getDescription() : purchase.getDescription());
            installment.setType(dto.getType() != null ? dto.getType().name() : purchase.getType().name());
            installment.setAmount(newAmount);
            installment.setTotalInstallmentsPlan(dto.getInstallments());
            installment.setCurrentInstallment(installmentNumber);
            installment.setInvoices(invoice);
            installment.setDate(invoice.getExpirationDate());
            installment.setUpdatedAt(dateNow);
            installmentsToSave.add(installment);
            applyInvoiceDelta(invoicesToUpdate, invoice, newAmount, dateNow);
        }

        installmentPlanService.saveAll(installmentsToSave);
        invoicesService.saveAll(invoicesToUpdate.values().stream().toList());

        if (dto.getName() != null) purchase.setName(dto.getName());
        if (dto.getDescription() != null) purchase.setDescription(dto.getDescription());
        if (dto.getType() != null) purchase.setType(dto.getType());
        purchase.setUpdatedAt(dateNow);
        purchase = repository.save(purchase);

        return TransactionResponseDTO.toDetailedDTO(purchase);
    }

    private List<BigDecimal> splitInstallmentAmount(BigDecimal total, int installments) {
        BigDecimal baseAmount = total.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        BigDecimal difference = total.subtract(baseAmount.multiply(BigDecimal.valueOf(installments)));
        List<BigDecimal> amounts = new ArrayList<>(installments);
        for (int index = 0; index < installments; index++) {
            amounts.add(index == 0 ? baseAmount.add(difference) : baseAmount);
        }
        return amounts;
    }

    private void validateInvoiceForInstallmentIncrease(Invoices invoice) {
        validateEditableInvoice(invoice);
        if (invoice.getCreditCard() == null || invoice.getMonth() == null || invoice.getYear() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Fatura sem dados completos para recalcular o parcelamento.");
        }
        LocalDate closeDate = invoiceDateService.calculateCloseDate(
                invoice.getCreditCard(),
                invoice.getMonth(),
                invoice.getYear()
        );
        LocalDate expirationDate = invoiceDateService.calculateExpirationDate(
                invoice.getCreditCard(),
                invoice.getMonth(),
                invoice.getYear()
        );
        String status = invoiceDateService.calculateInvoiceStatus(
                invoice.getCreditCard(),
                invoice.getPaid(),
                invoice.getAmount(),
                closeDate,
                expirationDate,
                invoice.getMonth(),
                invoice.getYear()
        );
        if (invoiceDateService.isClosedOrPaid(status)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar o parcelamento porque uma fatura envolvida está fechada ou paga.");
        }
    }

    private void validateInvoiceFinancialLinks(
            Invoices invoice,
            Transactions purchase,
            List<InstallmentPlan> purchaseInstallments
    ) {
        Set<UUID> canonicalInstallmentIds = purchaseInstallments.stream()
                .map(InstallmentPlan::getId)
                .collect(Collectors.toSet());

        for (InstallmentPlan item : installmentPlanService.findByInvoiceId(invoice.getId())) {
            if (item.getDeletedAt() != null || Boolean.FALSE.equals(item.getEnabled())) {
                continue;
            }
            if (item.getPurchaseId() == null) {
                throw invoiceItemWithoutAuditableOrigin();
            }
            if (item.getPurchaseId().equals(purchase.getId())) {
                if (!canonicalInstallmentIds.contains(item.getId())) {
                    throw new BadRequestException(
                            ConstsMessages.ERROR_TITLE,
                            "Não é possível alterar o parcelamento porque a compra possui desconto, estorno ou ajuste vinculado."
                    );
                }
                continue;
            }

            Transactions origin = repository.findByIdIncludingDeleted(item.getPurchaseId())
                    .orElseThrow(this::invoiceItemWithoutAuditableOrigin);
            if (origin.getType() == TransactionType.PAGAMENTO_FATURA) {
                if (origin.getDeletedAt() == null
                        && origin.getTargetInvoice() != null
                        && origin.getTargetInvoice().getId().equals(invoice.getId())) {
                    throw new BadRequestException(
                            ConstsMessages.ERROR_TITLE,
                            "Não é possível alterar o parcelamento porque uma fatura envolvida possui pagamento parcial ou total."
                    );
                }
                throw invoiceItemWithoutAuditableOrigin();
            }
            if (!isAuditablePurchaseItemFromSameInvoice(origin, item, invoice)) {
                throw invoiceItemWithoutAuditableOrigin();
            }
        }
    }

    private boolean isCanonicalPurchaseInstallment(InstallmentPlan installment, Transactions purchase) {
        return installment.getPurchaseId() != null
                && installment.getPurchaseId().equals(purchase.getId())
                && installment.getType() != null
                && purchase.getType() != null
                && installment.getType().equals(purchase.getType().name());
    }

    private boolean isAuditablePurchaseItemFromSameInvoice(
            Transactions origin,
            InstallmentPlan item,
            Invoices invoice
    ) {
        return origin.getDeletedAt() == null
                && origin.getCreditCard() != null
                && invoice.getCreditCard() != null
                && origin.getCreditCard().getId().equals(invoice.getCreditCard().getId())
                && origin.getUser() != null
                && item.getUser() != null
                && origin.getUser().getId().equals(item.getUser().getId());
    }

    private BadRequestException invoiceItemWithoutAuditableOrigin() {
        return new BadRequestException(
                ConstsMessages.ERROR_TITLE,
                "Não é possível alterar o parcelamento porque a fatura possui item sem origem financeira auditável."
        );
    }

    private boolean sameInvoicePeriod(Invoices invoice, LocalDateTime expectedInvoiceDate) {
        return invoice != null
                && invoice.getMonth() != null
                && invoice.getYear() != null
                && invoice.getMonth() == expectedInvoiceDate.getMonthValue()
                && invoice.getYear() == expectedInvoiceDate.getYear();
    }

    private String invoicePeriodKey(Integer month, Integer year) {
        return year + "-" + month;
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
                .map(TransactionResponseDTO::toDetailedDTO)
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

        if (transaction.getType() == TransactionType.PAGAMENTO_FATURA) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_PAYMENT_EDIT_BLOCKED);
        }

        if (transaction.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        validateRefuelDeletionAllowed(transaction);

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
            refuelMetricsService.recalculate(transaction.getVehicle());
        }
    }

    private void validateRefuelDeletionAllowed(Transactions transaction) {
        if (!VehicleTransactionRules.isRefuel(transaction) || transaction.getVehicle() == null) {
            return;
        }
        List<Transactions> refuels = repository.findActiveRefuelsByVehicleOrdered(transaction.getVehicle().getId());
        int index = -1;
        for (int i = 0; i < refuels.size(); i++) {
            if (refuels.get(i).getId().equals(transaction.getId())) {
                index = i;
                break;
            }
        }
        if (index >= 0 && index < refuels.size() - 1) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "Não é possível excluir este abastecimento porque isso afetaria o histórico e os cálculos do veículo. Exclua os abastecimentos em sequência, do último até o desejado."
            );
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
            } else if (inst.getInvoices().getCreditCard() != null && inst.getAmount() != null && inst.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                CreditCard card = inst.getInvoices().getCreditCard();
                card.consumeLimit(inst.getAmount().abs());
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
        OperationScope scope = normalizeScope(operationScope);
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }
        if (transaction.getType() == TransactionType.PAGAMENTO_FATURA || dto.getType() == TransactionType.PAGAMENTO_FATURA) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_PAYMENT_EDIT_BLOCKED);
        }

        boolean recurringTransaction = transaction.getRecurrenceRule() != null;
        if (recurringTransaction && scope == OperationScope.ALL) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Alteração de todos os lançamentos recorrentes não está disponível neste momento.");
        }
        boolean recurrenceScheduleChanged = recurringTransaction
                && scope == OperationScope.FROM_THIS_FORWARD
                && changesRecurrenceSchedule(transaction, dto);
        Long recurrenceReferenceDate = transaction.getDate();

        Accounts oldAccount = transaction.getAccount();
        BigDecimal oldAmount = transaction.getAmount();
        boolean wasPaid = transaction.getPaid();
        TransactionType oldType = transaction.getType();
        Vehicle oldVehicle = transaction.getVehicle();
        boolean oldWasRefuel = VehicleTransactionRules.isRefuel(transaction);

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
                if (scope == OperationScope.ONLY_THIS) {
                    transaction.setRecurrenceRule(null);
                } else if (scope != OperationScope.FROM_THIS_FORWARD) {
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

        boolean shouldGenerateProjectionsForUpdatedRule = false;
        if (scope == OperationScope.FROM_THIS_FORWARD && recurringTransaction) {
            if (recurrenceScheduleChanged) {
                shouldGenerateProjectionsForUpdatedRule = splitRecurringScheduleFromTransactionForward(
                        transaction,
                        dto,
                        recurrenceReferenceDate,
                        dateNow,
                        currentUser,
                        currentAccount
                );
            } else {
                applyRecurringSimpleChangesFromTransactionForward(
                        transaction,
                        dto,
                        recurrenceReferenceDate,
                        dateNow,
                        currentAccount,
                        currentCategory
                );
            }
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

        if (oldWasRefuel
                && oldVehicle != null
                && (savedTransaction.getVehicle() == null
                || !oldVehicle.getId().equals(savedTransaction.getVehicle().getId()))) {
            recalculateVehicleCurrentOdometer(oldVehicle);
            refuelMetricsService.recalculate(oldVehicle);
        }
        if (shouldRecalculateVehicleOdometer && savedTransaction.getVehicle() != null) {
            recalculateVehicleCurrentOdometer(savedTransaction.getVehicle());
            refuelMetricsService.recalculate(savedTransaction.getVehicle());
        }
        if (!shouldRecalculateVehicleOdometer && VehicleTransactionRules.isRefuel(savedTransaction)) {
            refuelMetricsService.recalculate(savedTransaction.getVehicle());
        }

        // 2. AGORA SIM GERA AS PROJEÇÕES (O banco já consegue enxergar a transação do passo 1)
        if (transformToFixed) {
            LocalDate limiteProjecao = LocalDate.now(DateUtils.zoneId).plusYears(1);
            generateProjectionsForRule(transaction.getRecurrenceRule(), limiteProjecao);
        }
        if (shouldGenerateProjectionsForUpdatedRule && transaction.getRecurrenceRule() != null) {
            LocalDate limiteProjecao = LocalDate.now(DateUtils.zoneId).plusYears(1);
            generateProjectionsForRule(transaction.getRecurrenceRule(), limiteProjecao);
        }

        return transaction;
    }

    private boolean changesRecurrenceSchedule(Transactions transaction, TransactionDTO dto) {
        RecurrenceRule rule = transaction.getRecurrenceRule();
        if (rule == null) {
            return false;
        }
        if (dto.getDate() != null && !Objects.equals(dto.getDate(), transaction.getDate())) {
            return true;
        }
        if (dto.getRecurrenceFrequency() != null && dto.getRecurrenceFrequency() != rule.getFrequency()) {
            return true;
        }
        if (dto.getRecurrenceEndDate() != null && !Objects.equals(dto.getRecurrenceEndDate(), rule.getEndDate())) {
            return true;
        }
        if (dto.getIsFixed() != null && !Objects.equals(dto.getIsFixed(), transaction.getFixed())) {
            return true;
        }
        return false;
    }

    private void applyRecurringSimpleChangesFromTransactionForward(
            Transactions transaction,
            TransactionDTO dto,
            Long referenceDate,
            long dateNow,
            Accounts account,
            Category category
    ) {
        RecurrenceRule rule = transaction.getRecurrenceRule();
        if (rule == null) {
            return;
        }

        if (dto.getName() != null) rule.setName(dto.getName());
        if (dto.getDescription() != null) rule.setDescription(dto.getDescription());
        if (dto.getAmount() != null) rule.setBaseAmount(dto.getAmount());
        if (dto.getType() != null) rule.setType(dto.getType());
        rule.setAccount(account);
        rule.setCategory(category);
        rule.setUpdatedAt(dateNow);
        recurrenceRuleService.save(rule);

        List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(rule.getId(), referenceDate).stream()
                .filter(tx -> tx.getDate() != null && tx.getDate() >= referenceDate)
                .filter(tx -> tx.getDeletedAt() == null)
                .filter(tx -> !Boolean.TRUE.equals(tx.getPaid()))
                .toList();

        for (Transactions tx : futureUnpaidTx) {
            if (dto.getName() != null) tx.setName(dto.getName());
            if (dto.getDescription() != null) tx.setDescription(dto.getDescription());
            if (dto.getType() != null) tx.setType(dto.getType());
            if (dto.getAmount() != null) tx.setAmount(dto.getAmount());
            tx.setAccount(account);
            tx.setCategory(category);
            tx.setUpdatedAt(dateNow);
        }
        if (!futureUnpaidTx.isEmpty()) {
            repository.saveAll(futureUnpaidTx);
        }
    }

    private boolean applyVehicleFieldsOnUpdate(Transactions transaction, TransactionDTO dto, Users currentUser) {
        if (transaction.getType() != TransactionType.DESPESA) {
            return false;
        }
        boolean wasRefuel = VehicleTransactionRules.isRefuel(transaction);
        Vehicle vehicle = transaction.getVehicle();
        boolean vehiclePayloadPresent = dto.getVehicleId() != null
                || dto.getCurrentOdometer() != null
                || dto.getLiters() != null
                || dto.getFuelType() != null
                || dto.getFullTank() != null
                || dto.getOdometerJumpConfirmed() != null;

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

        Double targetLiters = vehiclePayloadPresent ? dto.getLiters() : transaction.getLiters();
        var targetFuelType = vehiclePayloadPresent ? dto.getFuelType() : transaction.getFuelType();
        BigDecimal targetOdometer = dto.getCurrentOdometer() != null
                ? dto.getCurrentOdometer()
                : transaction.getCurrentOdometer();
        boolean targetRefuel = targetLiters != null
                && targetLiters > 0
                && targetOdometer != null
                && targetOdometer.signum() > 0;
        boolean targetFullTank = dto.getFullTank() != null
                ? Boolean.TRUE.equals(dto.getFullTank())
                : Boolean.TRUE.equals(transaction.getFullTank());

        if (targetFullTank && !targetRefuel) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Tanque cheio só pode ser informado em abastecimentos.");
        }

        if (!targetRefuel) {
            transaction.setCurrentOdometer(null);
            transaction.setLiters(null);
            transaction.setFuelType(null);
            transaction.setGasStation(null);
            transaction.setEfficiency(null);
            transaction.setFullTank(false);
            if (dto.getDrivingPredominance() != null) {
                transaction.setDrivingPredominance(dto.getDrivingPredominance());
            }
            return wasRefuel;
        }

        if (targetOdometer == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Informe o odômetro do abastecimento.");
        }

        boolean shouldValidateOdometer = dto.getCurrentOdometer() != null
                || dto.getDate() != null
                || !wasRefuel;
        boolean shouldRecalculateVehicleOdometer = !wasRefuel
                || dto.getLiters() != null
                || dto.getFuelType() != null
                || dto.getFullTank() != null;
        if (shouldValidateOdometer) {
            odometerTimelineService.validateReading(
                    vehicle,
                    transaction.getDate(),
                    targetOdometer,
                    transaction.getId(),
                    transaction.getCreatedAt(),
                    Boolean.TRUE.equals(dto.getOdometerJumpConfirmed())
            );
            shouldRecalculateVehicleOdometer = transaction.getCurrentOdometer() == null
                    || targetOdometer.compareTo(transaction.getCurrentOdometer()) != 0
                    || dto.getDate() != null
                    || shouldRecalculateVehicleOdometer;
        }

        transaction.setCurrentOdometer(targetOdometer);
        transaction.setLiters(targetLiters);
        transaction.setFuelType(targetFuelType);
        transaction.setFullTank(targetFullTank);
        transaction.setGasStation(null);
        if (dto.getDrivingPredominance() != null) {
            transaction.setDrivingPredominance(dto.getDrivingPredominance());
        }
        transaction.setEfficiency(null);
        return shouldRecalculateVehicleOdometer;
    }

    private boolean splitRecurringScheduleFromTransactionForward(Transactions transaction, TransactionDTO dto, Long referenceDate, long dateNow, Users currentUser, Accounts account) {
        RecurrenceRule oldRule = transaction.getRecurrenceRule();
        if (oldRule == null) {
            return false;
        }

        Long originalEndDate = oldRule.getEndDate();
        Long previousEnd = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(referenceDate).minusDays(1));
        oldRule.setEndDate(previousEnd);
        oldRule.setUpdatedAt(dateNow);
        recurrenceRuleService.save(oldRule);

        List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(oldRule.getId(), referenceDate).stream()
                .filter(tx -> !tx.getId().equals(transaction.getId()))
                .filter(tx -> tx.getDate() != null && tx.getDate() >= referenceDate)
                .filter(tx -> tx.getDeletedAt() == null)
                .filter(tx -> !Boolean.TRUE.equals(tx.getPaid()))
                .toList();

        for (Transactions tx : futureUnpaidTx) {
            tx.setDeletedAt(dateNow);
            tx.setUpdatedAt(dateNow);
        }
        if (!futureUnpaidTx.isEmpty()) {
            repository.saveAll(futureUnpaidTx);
        }

        if (Boolean.FALSE.equals(dto.getIsFixed())) {
            transaction.setRecurrenceRule(null);
            return false;
        }

        RecurrenceRule newRule = RecurrenceRule.builder()
                .id(ID.generate())
                .name(dto.getName() != null ? dto.getName() : transaction.getName())
                .description(dto.getDescription() != null ? dto.getDescription() : transaction.getDescription())
                .baseAmount(dto.getAmount() != null ? dto.getAmount() : transaction.getAmount())
                .type(dto.getType() != null ? dto.getType() : transaction.getType())
                .frequency(dto.getRecurrenceFrequency() != null ? dto.getRecurrenceFrequency() : oldRule.getFrequency())
                .startDate(transaction.getDate())
                .endDate(dto.getRecurrenceEndDate() != null ? dto.getRecurrenceEndDate() : originalEndDate)
                .status(RuleStatus.ACTIVE)
                .createdAt(dateNow)
                .user(currentUser)
                .category(transaction.getCategory())
                .account(account)
                .targetAccount(oldRule.getTargetAccount())
                .build();
        newRule = recurrenceRuleService.save(newRule);
        transaction.setRecurrenceRule(newRule);

        return true;
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

    private void validateVehicleOdometerOnCreate(TransactionDTO dto, Users user) {
        if (dto.getType() != TransactionType.DESPESA
                || dto.getVehicleId() == null) {
            return;
        }

        Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        if (Boolean.TRUE.equals(dto.getFullTank()) && !VehicleTransactionRules.isRefuel(dto)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Tanque cheio só pode ser informado em abastecimentos.");
        }
        if (!VehicleTransactionRules.isRefuel(dto)) {
            return;
        }
        if (dto.getCurrentOdometer() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Informe o odômetro do abastecimento.");
        }

        odometerTimelineService.validateReading(
                vehicle,
                dto.getDate(),
                dto.getCurrentOdometer(),
                null,
                Long.MAX_VALUE,
                Boolean.TRUE.equals(dto.getOdometerJumpConfirmed())
        );
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
        odometerTimelineService.recalculateCurrentOdometer(vehicle);
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
        if (rule.getType() == TransactionType.TRANSFERENCIA) {
            rule.setCategory(categoryService.findTransferCategory(rule.getUser()));
        }
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
        if (newOrigin.getId().equals(newDest.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A conta de origem e destino devem ser diferentes.");
        }

        BigDecimal amount = dto.getAmount() != null ? dto.getAmount() : transferOut.getAmount();
        Long date = dto.getDate() != null ? dto.getDate() : transferOut.getDate();
        Boolean paid = dto.getPaid() != null ? dto.getPaid() : transferOut.getPaid();
        String name = dto.getName() != null ? dto.getName() : transferOut.getName();
        String description = dto.getDescription() != null ? dto.getDescription() : transferOut.getDescription();
        Category transferCategory = categoryService.findTransferCategory(currentUser);

        transferOut.setName(name);
        transferOut.setDescription(description);
        transferOut.setAmount(amount);
        transferOut.setDate(date);
        transferOut.setPaid(paid);
        transferOut.setAccount(newOrigin);
        transferOut.setCategory(transferCategory);
        transferOut.setType(TransactionType.TRANSFERENCIA_SAIDA);
        transferOut.setUpdatedAt(dateNow);

        transferIn.setName(name);
        transferIn.setDescription(description);
        transferIn.setAmount(amount);
        transferIn.setDate(date);
        transferIn.setPaid(paid);
        transferIn.setAccount(newDest);
        transferIn.setCategory(transferCategory);
        transferIn.setType(TransactionType.TRANSFERENCIA_ENTRADA);
        transferIn.setParentTransaction(transferOut);
        transferIn.setUpdatedAt(dateNow);

        if (transferOut.getRecurrenceRule() != null) {
            transferOut.getRecurrenceRule().setCategory(transferCategory);
        }

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
            RecurrenceRule rule = transferOut.getRecurrenceRule();
            rule.setCategory(transferCategory);
            rule.setUpdatedAt(dateNow);
            recurrenceRuleService.save(rule);

            List<Transactions> futureUnpaidTransfers =
                    repository.findFutureUnpaidByRuleId(rule.getId(), transferOut.getDate());
            futureUnpaidTransfers.forEach(transaction -> {
                transaction.setCategory(transferCategory);
                transaction.setUpdatedAt(dateNow);
            });
            repository.saveAll(futureUnpaidTransfers);
            cascadeRuleUpdate(rule.getId(), amount);
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

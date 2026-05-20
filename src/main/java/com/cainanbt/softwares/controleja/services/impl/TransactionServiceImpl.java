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
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
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
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
            // Se informou odômetro, atualiza no cadastro do veículo
            if (dto.getCurrentOdometer() != null) {
                vehicleService.updateOdometer(tx.getVehicle(), dto.getCurrentOdometer());
            }

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

        applyVehicleConsolidation(responseList, start, end);
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

    private void applyVehicleConsolidation(List<TransactionResponseDTO> responseList, Long start, Long end) {
        BigDecimal totalVeiculos = BigDecimal.ZERO;
        boolean allVehicleExpensesPaid = true;
        Iterator<TransactionResponseDTO> iterator = responseList.iterator();
        while (iterator.hasNext()) {
            TransactionResponseDTO tx = iterator.next();

            boolean isVeiculo = tx.getVehicleId() != null;

            if (isVeiculo && tx.getType() == TransactionType.DESPESA) {
                totalVeiculos = totalVeiculos.add(tx.getAmount());
                if (!Boolean.TRUE.equals(tx.getPaid())) {
                    allVehicleExpensesPaid = false;
                }
                iterator.remove();
            }
        }
        if (totalVeiculos.compareTo(BigDecimal.ZERO) > 0) {
            String namespaceVirtual = "VEHICLE_CONSOLIDATED_" + start + "_" + end;
            UUID idDeterministico = UUID.nameUUIDFromBytes(namespaceVirtual.getBytes(StandardCharsets.UTF_8));

            TransactionResponseDTO veiculoConsolidado = new TransactionResponseDTO();
            veiculoConsolidado.setId(idDeterministico);
            veiculoConsolidado.setName("Despesas de veículos do mês");
            veiculoConsolidado.setAmount(totalVeiculos);
            veiculoConsolidado.setDate(end);
            veiculoConsolidado.setPaid(allVehicleExpensesPaid);
            veiculoConsolidado.setType(TransactionType.DESPESA);
            veiculoConsolidado.setCategoryName("Veículos");
            veiculoConsolidado.setAccountName("Consolidado");
            veiculoConsolidado.setVirtual(true);

            responseList.add(veiculoConsolidado);
        }
    }

    @Override
    @Transactional
    public TransactionResponseDTO updateTransactionDTO(UUID id, TransactionDTO dto, Boolean updateFuture) {
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

        Transactions current = findByIdOrThrow(id);
        if (isTransferSide(current)) {
            return TransactionResponseDTO.toDTO(updateTransferPair(current, dto, updateFuture));
        }

        Transactions transaction = updateTransaction(id, dto, updateFuture);

        return TransactionResponseDTO.toDTO(transaction);
    }

    @Override
    public List<TransactionResponseDTO> getTransactionsTypeVehicle(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        List<Transactions> transactions = repository.findTransactionsByMonth(user.getId(), start, end);

        return transactions.stream()
                .map(TransactionResponseDTO::toDTO)
                .filter(tx -> tx.getVehicleId() != null)
                .filter(tx -> tx.getType() == TransactionType.DESPESA)
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .toList();
    }

    @Override
    @Transactional
    public void softDelete(UUID id, Boolean cancelFuture) {
        Optional<InstallmentPlan> instOpt = installmentPlanService.findById(id);

        if (instOpt.isPresent()) {
            InstallmentPlan inst = instOpt.get();
            inst.setDeletedAt(DateUtils.getEpochNow());
            installmentPlanService.save(inst);

            Invoices inv = inst.getInvoices();
            inv.setAmount(inv.getAmount().subtract(inst.getAmount()));
            invoicesService.save(inv);
            return;
        }

        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (isTransferSide(transaction)) {
            deleteTransferPair(transaction, cancelFuture, dateNow, currentUser);
            return;
        }

        if (transaction.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        if (Boolean.TRUE.equals(cancelFuture)) {
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
    public Transactions updateTransaction(UUID id, TransactionDTO dto, Boolean updateFuture) {
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

            // O Efeito Cascata funciona paralelo porque a regra JÁ existe no banco há tempos.
            if (Boolean.TRUE.equals(updateFuture) && transaction.getRecurrenceRule() != null) {
                UUID ruleId = transaction.getRecurrenceRule().getId();
                BigDecimal newAmount = dto.getAmount();
                CompletableFuture.runAsync(() -> {
                    try {
                        cascadeRuleUpdate(ruleId, newAmount);
                    } catch (Exception e) {
                        log.error("Erro ao aplicar efeito cascata na regra: " + ruleId, e);
                    }
                });
            }
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

        if (dto.getCategoryId() != null) {
            Category category = categoryService.findByIdOrThrow(dto.getCategoryId());
            transaction.setCategory(category);
        }

        applyVehicleFieldsOnUpdate(transaction, dto, currentUser);

        if (transaction.getPaid() && currentAccount.getType() != AccountType.CREDIT_CARD) {
            if (transaction.getType() == TransactionType.DESPESA) currentAccount.debit(transaction.getAmount());
            else if (transaction.getType() == TransactionType.RECEITA) currentAccount.credit(transaction.getAmount());
            accountsService.update(currentAccount);
        }

        transaction.setUpdatedAt(dateNow);

        // 1. SALVA A TRANSAÇÃO ATUAL NO BANCO COM A REGRA ANEXADA
        transaction = repository.save(transaction);
        final Transactions savedTransaction = transaction;

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

    private void applyVehicleFieldsOnUpdate(Transactions transaction, TransactionDTO dto, Users currentUser) {
        Vehicle vehicle = transaction.getVehicle();
        if (dto.getVehicleId() != null) {
            vehicle = vehicleService.findById(dto.getVehicleId());
            if (!vehicle.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
            }
            transaction.setVehicle(vehicle);
        }

        if (vehicle == null) {
            return;
        }

        if (dto.getCurrentOdometer() != null) {
            transaction.setCurrentOdometer(dto.getCurrentOdometer());
            vehicleService.updateOdometer(vehicle, dto.getCurrentOdometer());
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
    protected Transactions updateTransferPair(Transactions current, TransactionDTO dto, Boolean updateFuture) {
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

        if (Boolean.TRUE.equals(updateFuture) && transferOut.getRecurrenceRule() != null) {
            cascadeRuleUpdate(transferOut.getRecurrenceRule().getId(), amount);
        }

        repository.saveAll(List.of(transferOut, transferIn));
        return transferOut;
    }

    private void deleteTransferPair(Transactions current, Boolean cancelFuture, long dateNow, Users currentUser) {
        TransferPair pair = findTransferPair(current);
        validateTransferPairOwner(pair, currentUser);

        Transactions transferOut = pair.out();
        Transactions transferIn = pair.in();

        if (Boolean.TRUE.equals(cancelFuture) && transferOut.getRecurrenceRule() != null) {
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

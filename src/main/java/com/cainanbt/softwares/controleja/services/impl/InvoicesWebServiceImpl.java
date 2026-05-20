package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceItemDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
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

    @Override
    public Optional<InvoiceDetailsDTO> getInvoiceDetails(UUID cardId, Integer month, Integer year) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        Optional<Invoices> invOpt = invoicesService.findByCreditCardIdAndMonthAndYear(cardId, month, year);

        // If invoice not found, return a phantom DTO with calculated dates and "SEM GASTOS"
        if (invOpt.isEmpty()) {
            // Fetch card to build phantom values
            CreditCard card = creditCardService.findById(cardId).orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado."));
            if (!card.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException("Acesso Negado", "Cartão não pertence ao usuário autenticado.");
            }

            // Calculate closeDate and expirationDate phantom values
            LocalDate closeLocal;
            try {
                closeLocal = LocalDate.of(year, month, card.getCloseDay());
            } catch (DateTimeException e) {
                closeLocal = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            }
            if (card.getCloseDay() > card.getBestDay()) {
                closeLocal = closeLocal.minusMonths(1);
            }
            long closeEpoch = DateUtils.localDateToEpoch(closeLocal);

            // expiration date: use bestDay in the target month
            LocalDate expLocal;
            try {
                expLocal = LocalDate.of(year, month, Math.min(card.getBestDay(), LocalDate.of(year, month, 1).lengthOfMonth()));
            } catch (DateTimeException e) {
                expLocal = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            }
            long expEpoch = DateUtils.localDateToEpoch(expLocal);

            InvoiceDetailsDTO phantom = InvoiceDetailsDTO.builder()
                    .invoiceId(null)
                    .cardId(card.getId())
                    .cardName(card.getName())
                    .month(month)
                    .year(year)
                    .totalAmount(BigDecimal.ZERO)
                    .expirationDate(expEpoch)
                    .closeDate(closeEpoch)
                    .status("SEM GASTOS")
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

        List<InvoiceItemDTO> itemDTOs = items.stream().map(i -> InvoiceItemDTO.builder()
                .id(i.getId())
                .date(i.getDate())
                .name(i.getName())
                .currentInstallment(i.getCurrentInstallment())
                .totalInstallmentsPlan(i.getTotalInstallmentsPlan())
                .amount(i.getAmount())
                .build()).collect(Collectors.toList());

        String status = calculateInvoiceStatus(inv);
        Long closeDate = calculateCloseEpoch(inv);

        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder()
                .invoiceId(inv.getId())
                .cardId(inv.getCreditCard() != null ? inv.getCreditCard().getId() : null)
                .cardName(inv.getCreditCard() != null ? inv.getCreditCard().getName() : null)
                .month(inv.getMonth())
                .year(inv.getYear())
                .totalAmount(inv.getAmount())
                .expirationDate(inv.getExpirationDate())
                .closeDate(closeDate)
                .status(status)
                .items(itemDTOs)
                .build();

        return Optional.of(dto);
    }

    private long calculateCloseEpoch(Invoices inv) {
        try {
            LocalDate closeDate = LocalDate.of(inv.getYear(), inv.getMonth(), inv.getCreditCard().getCloseDay());
            // CORREÇÃO: Se dia de fechamento é maior que o vencimento, ela fecha no mês anterior
            if (inv.getCreditCard().getCloseDay() > inv.getCreditCard().getBestDay()) {
                closeDate = closeDate.minusMonths(1);
            }
            return DateUtils.localDateToEpoch(closeDate);
        } catch (Exception e) {
            LocalDate closeDate = LocalDate.of(inv.getYear(), inv.getMonth(), 1).with(TemporalAdjusters.lastDayOfMonth());
            return DateUtils.localDateToEpoch(closeDate);
        }
    }

    private String calculateInvoiceStatus(Invoices invoice) {
        if (Boolean.TRUE.equals(invoice.getPaid())) return "PAGA";

        LocalDate today = LocalDate.now(DateUtils.zoneId);
        long todayEpoch = DateUtils.localDateToEpoch(today);

        if (invoice.getExpirationDate() < todayEpoch) return "ATRASADA";

        Long closeDateEpoch = calculateCloseEpoch(invoice);
        if (todayEpoch >= closeDateEpoch) return "FECHADA";

        return "ABERTA";
    }

    @Override
    public List<AdvanceablePurchaseDTO> getAdvanceablePurchases(UUID cardId, Integer month, Integer year) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        CreditCard card = creditCardService.findById(cardId).orElseThrow(() -> new BadRequestException("Erro", "Cartão não encontrado."));
        if (!card.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Cartão não pertence ao usuário autenticado.");
        }

        // Calculate the close date epoch for the requested invoice month/year
        LocalDate closeLocal;
        try {
            closeLocal = LocalDate.of(year, month, card.getCloseDay());
        } catch (DateTimeException e) {
            closeLocal = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        }
        if (card.getCloseDay() > card.getBestDay()) {
            closeLocal = closeLocal.minusMonths(1);
        }
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
}


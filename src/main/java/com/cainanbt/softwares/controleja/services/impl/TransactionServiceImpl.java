package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.exceptions.models.InternalServerException;
import com.cainanbt.softwares.controleja.exceptions.models.NotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;
    private final VehicleService vehicleService;

    public TransactionServiceImpl(TransactionRepository transactionRepository, AccountsService accountsService, CategoryService categoryService, CreditCardService creditCardService, VehicleService vehicleService) {
        this.repository = transactionRepository;
        this.accountsService = accountsService;
        this.categoryService = categoryService;
        this.creditCardService = creditCardService;
        this.vehicleService = vehicleService;
    }

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        try {
            Accounts account = accountsService.findById(dto.getAccountId())
                    .orElseThrow(() -> new NotFoundException("Conta não encontrada", "A conta especificada não existe."));

            if (!account.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException("Acesso negado", "A conta não pertence ao usuário.");
            }

            Category category = categoryService.findById(dto.getCategoryId())
                    .orElseThrow(() -> new NotFoundException("Categoria não encontrada", "A categoria especificada não existe."));

            if (dto.getType() == TransactionType.PAGAMENTO_FATURA) {
                return processInvoicePayment(dto, account, category, user);
            }

            if (account.getType() == AccountType.CREDIT_CARD) {
                return processCreditCardExpense(dto, account, category, user);
            }

            return processNormalTransaction(dto, account, category, user);
        } catch (NotFoundException | ForbiddenException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException("Erro ao criar transação", "Não foi possível criar a transação. Tente novamente.", e);
        }
    }

    private Transactions processInvoicePayment(TransactionDTO dto, Accounts sourceAccount, Category category, Users user) {
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException("Erro", "Para pagar fatura, informe o ID da conta do cartão (targetAccountId).");
        }
        Accounts cardAccount = accountsService.findById(dto.getTargetAccountId())
                .orElseThrow(() -> new NotFoundException("Conta do cartão não encontrada", "A conta do cartão especificada não existe."));

        if (cardAccount.getType() != AccountType.CREDIT_CARD) {
            throw new BadRequestException("Erro", "A conta de destino deve ser um Cartão de Crédito.");
        }

        sourceAccount.debit(dto.getAmount());
        accountsService.update(sourceAccount);

        cardAccount.credit(dto.getAmount());
        accountsService.update(cardAccount);

        CreditCard card = creditCardService.findByAccountId(cardAccount.getId());
        card.restoreLimit(dto.getAmount());
        creditCardService.updateLimit(card);

        Transactions t = createBaseTransactionBuilder(dto, sourceAccount, category, user)
                .paid(true)
                .build();

        return repository.save(t);
    }

    private Transactions processNormalTransaction(TransactionDTO dto, Accounts account, Category category, Users user) {
        Transactions t = createBaseTransactionBuilder(dto, account, category, user).build();

        if (Boolean.TRUE.equals(t.getPaid())) {
            if (t.getType() == TransactionType.DESPESA) {
                account.debit(t.getAmount());
            } else if (t.getType() == TransactionType.RECEITA) {
                account.credit(t.getAmount());
            }
            accountsService.update(account);
        }
        return repository.save(t);
    }

    private Transactions processCreditCardExpense(TransactionDTO dto, Accounts account, Category category, Users user) {
        if (dto.getType() != TransactionType.DESPESA) {
            throw new BadRequestException("Erro", "Em conta de cartão, use apenas DESPESA.");
        }

        CreditCard card = creditCardService.findByAccountId(account.getId());
        card.consumeLimit(dto.getAmount());
        creditCardService.updateLimit(card);

        int parcelas = (dto.getInstallments() == null || dto.getInstallments() < 1) ? 1 : dto.getInstallments();

        BigDecimal valorParcela = dto.getAmount().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal somaParcelas = valorParcela.multiply(BigDecimal.valueOf(parcelas));
        BigDecimal diferenca = dto.getAmount().subtract(somaParcelas); // O que sobrou (ex: 0.01)

        LocalDateTime dataBase = LocalDateTime.ofInstant(Instant.ofEpochMilli(dto.getDate()), ZoneId.systemDefault());
        Transactions first = null;

        for (int i = 0; i < parcelas; i++) {
            long dataVencimento = dataBase.plusMonths(i).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            BigDecimal valorDestaParcela = valorParcela;
            if (i == 0) {
                valorDestaParcela = valorDestaParcela.add(diferenca);
            }
            String nomeParcelado = dto.getName() + (parcelas > 1 ? " (" + (i + 1) + "/" + parcelas + ")" : "");
            Transactions t = createBaseTransactionBuilder(dto, account, category, user)
                    .name(nomeParcelado)
                    .amount(valorDestaParcela)
                    .date(dataVencimento)
                    .paid(false)
                    .build();
            Transactions saved = repository.save(t);
            if (i == 0) first = saved;
            account.debit(valorDestaParcela);
        }
        accountsService.update(account);
        return first;
    }

    private Transactions.TransactionsBuilder createBaseTransactionBuilder(TransactionDTO dto, Accounts account, Category category, Users user) {
        var builder = Transactions.builder()
                .id(ID.generate())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .paid(dto.getPaid())
                .fixed(false)
                .enabled(true)
                .account(account)
                .category(category)
                .user(user)
                .createdAt(System.currentTimeMillis());

        if (dto.getVehicleId() != null) {
            Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
            if (!vehicle.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException("Acesso negado", "O veículo não pertence ao usuário.");
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

    @Override
    public List<Transactions> listLastTransactions() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdOrderByDateDesc(user.getId());
    }
}
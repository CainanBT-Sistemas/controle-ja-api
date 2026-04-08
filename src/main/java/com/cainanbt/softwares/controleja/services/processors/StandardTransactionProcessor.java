package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class StandardTransactionProcessor implements TransactionProcessor {

    private final TransactionRepository repository;
    private final TransactionHelper helper;

    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return account.getType() != AccountType.CREDIT_CARD
                && dto.getType() != TransactionType.TRANSFERENCIA
                && dto.getType() != TransactionType.PAGAMENTO_FATURA;
    }

    @Override
    public Transactions process(TransactionDTO dto, Accounts account, Category category, Users user) {
        int parcelas = (dto.getInstallments() == null || dto.getInstallments() < 1) ? 1 : dto.getInstallments();

        // CENÁRIO 1: Despesa/Receita FIXA (Netflix/Salário)
        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            Transactions.TransactionsBuilder baseBuilder = helper.createBaseTransactionBuilder(dto, account, category, user);
            RecurrenceRule rule = helper.createRecurrenceRule(dto, dto.getType(), DateUtils.getEpochNow(), user, account, null, category);
            baseBuilder.recurrenceRule(rule);

            Transactions tx = baseBuilder.build();
            helper.applyAccountBalance(tx, account);
            return repository.save(tx);
        }

        // CENÁRIO 2: Financiamento / Carnê
        if (parcelas > 1) {
            BigDecimal valorParcela = dto.getAmount().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
            BigDecimal diferenca = dto.getAmount().subtract(valorParcela.multiply(BigDecimal.valueOf(parcelas)));

            LocalDateTime dataCompra = DateUtils.epochToLocalDateTime(dto.getDate());
            Transactions primeiraParcela = null;

            for (int i = 0; i < parcelas; i++) {
                BigDecimal valorDestaParcela = (i == 0) ? valorParcela.add(diferenca) : valorParcela;
                long dataDestaParcela = DateUtils.localDateTimeToEpoch(dataCompra.plusMonths(i));
                String nomeParcela = dto.getName() + " (" + (i + 1) + "/" + parcelas + ")";

                Transactions tx = helper.createBaseTransactionBuilder(dto, account, category, user)
                        .name(nomeParcela)
                        .amount(valorDestaParcela)
                        .date(dataDestaParcela)
                        .paid(i == 0 ? dto.getPaid() : false)
                        .fixed(false)
                        .build();

                tx = repository.save(tx);

                if (i == 0) {
                    primeiraParcela = tx;
                    helper.applyAccountBalance(tx, account);
                } else {
                    tx.setParentTransaction(primeiraParcela);
                    repository.save(tx);
                }
            }
            return primeiraParcela;
        }

        // CENÁRIO 3: Transação Normal (Compra do Pão)
        Transactions tx = helper.createBaseTransactionBuilder(dto, account, category, user).build();
        helper.applyAccountBalance(tx, account);
        return repository.save(tx);
    }
}
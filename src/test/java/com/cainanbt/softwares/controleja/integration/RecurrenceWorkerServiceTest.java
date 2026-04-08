package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.repositories.RecurrenceRuleRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.workers.RecurrenceWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecurrenceWorkerServiceTest extends BaseTest {

    @Autowired
    private RecurrenceWorkerService workerService;
    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        transactionRepository.deleteAll();
        recurrenceRuleRepository.deleteAll();

        Users user = Users.builder().id(ID.generate()).username("Worker Tester").email("worker_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com").password("123456").enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).role("USER").oauth2User(false).createdAt(DateUtils.getEpochNow()).build();
        usersRepository.save(user);
        userId = user.getId();

        Accounts acc = Accounts.builder().id(ID.generate()).name("Conta Teste").type(AccountType.WALLET).institution("").currency("BRL").currentBalance(BigDecimal.ZERO).initialBalance(BigDecimal.ZERO).calculateBalance(true).enabled(true).user(user).createdAt(DateUtils.getEpochNow()).build();
        accountsRepository.save(acc);

        Category cat = Category.builder().id(ID.generate()).name("Fixa").categoryType("DESPESA").enabled(true).isSubCategory(false).isDefault(false).user(user).createdAt(DateUtils.getEpochNow()).build();
        categoryRepository.save(cat);

        RecurrenceRule rule = RecurrenceRule.builder().id(ID.generate()).name("Netflix").baseAmount(new BigDecimal("50.00")).type(TransactionType.DESPESA).frequency(RecurrenceFrequency.MONTHLY).startDate(DateUtils.getEpochNow()).status(RuleStatus.ACTIVE).createdAt(DateUtils.getEpochNow()).user(user).category(cat).account(acc).build();
        recurrenceRuleRepository.save(rule);
    }

    @Test
    @DisplayName("Motor deve ser Idempotente (Não duplicar transações se rodar múltiplas vezes)")
    void shouldBeIdempotentWhenRunningMultipleTimes() {
        // Primeira execução de madrugada (Gera as parcelas do ano inteiro)
        workerService.processProjections();

        List<Transactions> firstRunTx = transactionRepository.findByUserIdOrderByDateDesc(userId);
        int generatedCount = firstRunTx.size();

        assertTrue(generatedCount >= 12, "Deveria ter gerado as projeções de 1 ano");

        // Segunda execução do Worker (Rodou de novo por engano ou bug no agendador)
        workerService.processProjections();

        List<Transactions> secondRunTx = transactionRepository.findByUserIdOrderByDateDesc(userId);

        // A proteção Idempotente garante que nada foi duplicado!
        assertEquals(generatedCount, secondRunTx.size(), "O motor não é idempotente! Ele duplicou as transações ao rodar de novo.");
    }
}
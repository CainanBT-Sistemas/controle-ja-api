package com.cainanbt.softwares.controleja.services.users;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centraliza a criacao dos dados padrao que todo usuario precisa ao entrar no sistema.
 */
@Component
@RequiredArgsConstructor
public class UserDefaultDataInitializer {

    private final CategoryService categoryService;
    private final AccountsService accountsService;

    /**
     * Cria carteira padrao e arvore inicial de categorias para um usuario novo ou reinicializado.
     */
    public void initialize(Users user) {
        long now = DateUtils.getEpochNow();
        createDefaultWallet(user, now);
        createDefaultCategories(user, now);
    }

    /**
     * Cria a conta carteira padrao usada como primeira conta operacional do usuario.
     */
    private void createDefaultWallet(Users user, long now) {
        Accounts wallet = Accounts.builder()
                .id(ID.generate())
                .name("Minha Carteira")
                .type(AccountType.WALLET)
                .institution("")
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(true)
                .enabled(true)
                .icon("account_balance_wallet")
                .color("#42A5F5")
                .isDefault(true)
                .user(user)
                .createdAt(now)
                .build();
        accountsService.save(wallet);
    }

    /**
     * Cria as categorias padrao, incluindo a arvore veicular obrigatoria.
     */
    private void createDefaultCategories(Users user, long now) {
        createDefaultCategory(user, "Alimentação", TransactionType.DESPESA.name(), "restaurant", "#FFCA28", now, null);
        createDefaultCategory(user, "Moradia", TransactionType.DESPESA.name(), "home", "#FF5252", now, null);
        createDefaultCategory(user, "Transporte", TransactionType.DESPESA.name(), "directions_car", "#42A5F5", now, null);
        createDefaultCategory(user, "Saúde", TransactionType.DESPESA.name(), "medical_services", "#66BB6A", now, null);
        createDefaultCategory(user, "Lazer", TransactionType.DESPESA.name(), "sports_esports", "#AB47BC", now, null);
        createDefaultCategory(user, "Educação", TransactionType.DESPESA.name(), "school", "#EC407A", now, null);
        createDefaultCategory(user, "Mercado", TransactionType.DESPESA.name(), "shopping_cart", "#FFA726", now, null);
        createDefaultCategory(user, "Contas Fixas", TransactionType.DESPESA.name(), "receipt_long", "#8D6E63", now, null);
        createDefaultCategory(user, "Vestuário", TransactionType.DESPESA.name(), "checkroom", "#26A69A", now, null);
        createDefaultCategory(user, "Pets", TransactionType.DESPESA.name(), "pets", "#795548", now, null);

        Category vehicle = createDefaultCategory(user, "Veículo", TransactionType.DESPESA.name(), "directions_car", "#3F51B5", now, null);
        createDefaultCategory(user, "Abastecimento", TransactionType.DESPESA.name(), "local_gas_station", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Manutenção", TransactionType.DESPESA.name(), "build", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Seguro", TransactionType.DESPESA.name(), "health_and_safety", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Impostos (IPVA/Lic.)", TransactionType.DESPESA.name(), "receipt", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Multas", TransactionType.DESPESA.name(), "gavel", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Estacionamento/Pedágio", TransactionType.DESPESA.name(), "toll", "#3F51B5", now, vehicle);
        createDefaultCategory(user, "Estética", TransactionType.DESPESA.name(), "local_car_wash", "#3F51B5", now, vehicle);

        createDefaultCategory(user, "Reajuste de Saldo", TransactionType.REAJUSTE_SALDO.name(), "sync", "#9E9E9E", now, null);
        createDefaultCategory(user, "Transferência", TransactionType.TRANSFERENCIA.name(), "sync", "#9E9E9E", now, null);
        createDefaultCategory(user, "Salário", TransactionType.RECEITA.name(), "attach_money", "#00E676", now, null);
        createDefaultCategory(user, "Investimentos", TransactionType.RECEITA.name(), "trending_up", "#2979FF", now, null);
        createDefaultCategory(user, "Outros", TransactionType.RECEITA.name(), "category", "#BDBDBD", now, null);
    }

    /**
     * Persiste uma categoria padrao mantendo o relacionamento com a categoria pai quando existir.
     */
    private Category createDefaultCategory(Users user, String name, String type, String icon, String color, long now, Category parentCategory) {
        return categoryService.save(Category.builder()
                .id(ID.generate())
                .name(name)
                .categoryType(type)
                .enabled(true)
                .isSubCategory(parentCategory != null)
                .isDefault(true)
                .icon(icon)
                .color(color)
                .user(user)
                .createdAt(now)
                .subCategory(parentCategory)
                .build());
    }
}

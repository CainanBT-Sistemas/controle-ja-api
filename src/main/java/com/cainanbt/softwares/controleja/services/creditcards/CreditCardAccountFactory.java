package com.cainanbt.softwares.controleja.services.creditcards;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.utils.ID;

import java.math.BigDecimal;

/**
 * Cria e mantém a conta espelho usada para representar faturas de cartão.
 */
public class CreditCardAccountFactory {

    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String DEFAULT_ICON = "credit_card";
    private static final String DEFAULT_COLOR = "#9C27B0";

    /**
     * Monta a conta contábil vinculada ao cartão sem misturar regra de persistência.
     */
    public Accounts createInvoiceAccount(CreditCardDTO dto, Users user, long now) {
        return Accounts.builder()
                .id(ID.generate())
                .name(formatInvoiceAccountName(dto.getName()))
                .type(AccountType.CREDIT_CARD)
                .institution(normalizeName(dto.getName()))
                .currency(DEFAULT_CURRENCY)
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(false)
                .enabled(true)
                .user(user)
                .createdAt(now)
                .icon(resolveIcon(dto))
                .color(resolveColor(dto))
                .build();
    }

    /**
     * Sincroniza nome, instituição e identidade visual da conta espelho quando o cartão é editado.
     */
    public void applyCardChangesToAccount(Accounts account, CreditCardDTO dto) {
        if (account == null) {
            return;
        }
        if (dto.getName() != null) {
            account.setName(formatInvoiceAccountName(dto.getName()));
            account.setInstitution(normalizeName(dto.getName()));
        }
        if (dto.getIcon() != null) {
            account.setIcon(dto.getIcon());
        }
        if (dto.getColor() != null) {
            account.setColor(dto.getColor());
        }
    }

    /**
     * Resolve o ícone padrão do cartão quando o cliente não informa um valor.
     */
    public String resolveIcon(CreditCardDTO dto) {
        return dto.getIcon() != null ? dto.getIcon() : DEFAULT_ICON;
    }

    /**
     * Resolve a cor padrão do cartão quando o cliente não informa um valor.
     */
    public String resolveColor(CreditCardDTO dto) {
        return dto.getColor() != null ? dto.getColor() : DEFAULT_COLOR;
    }

    /**
     * Padroniza o nome da conta criada para armazenar lançamentos de fatura.
     */
    private String formatInvoiceAccountName(String name) {
        return normalizeName(name) + " (Fatura)";
    }

    /**
     * Remove espaços externos e evita nome nulo na conta espelho.
     */
    private String normalizeName(String name) {
        return name != null && !name.trim().isEmpty() ? name.trim() : "";
    }
}

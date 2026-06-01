package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsService {
    /**
     * Cria uma conta financeira para o usuário autenticado.
     */
    Accounts createAccount(AccountDTO dto);

    /**
     * Busca uma conta ativa por ID sem validar propriedade; usado por fluxos internos que já validam contexto.
     */
    Optional<Accounts> findById(UUID id);

    /**
     * Busca uma conta ativa por ID ou lança erro de entidade não encontrada.
     */
    Accounts findByIdOrThrow(UUID id);

    /**
     * Busca uma conta ativa garantindo que pertence ao usuário autenticado.
     */
    Accounts findMyAccountById(UUID id);

    /**
     * Lista contas financeiras visíveis do usuário, excluindo contas espelho de cartão de crédito.
     */
    List<Accounts> listMyAccountsExceptCrediCard();

    /**
     * Persiste alterações internas em uma conta já carregada.
     */
    Accounts update(Accounts accounts);

    /**
     * Atualiza dados cadastrais editáveis de uma conta do usuário autenticado.
     */
    Accounts updateAccount(UUID id, AccountDTO dto);

    /**
     * Remove logicamente uma conta do usuário autenticado.
     */
    void softDelete(UUID id);

    /**
     * Salva uma conta nova ou já montada por fluxos internos.
     */
    Accounts save(Accounts accounts);
}

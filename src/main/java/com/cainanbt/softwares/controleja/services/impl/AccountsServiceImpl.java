package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.accounts.AccountDomainValidator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class AccountsServiceImpl implements AccountsService {

    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String DEFAULT_ICON = "account_balance";
    private static final String DEFAULT_COLOR = "#42A5F5";

    private final AccountDomainValidator accountDomainValidator = new AccountDomainValidator();

    private final AccountsRepository repository;

    /**
     * Cria uma conta financeira do usuário autenticado, aplicando padrões visuais e bloqueando duplicidade.
     */
    @Override
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        String normalizedName = normalizeName(dto.getName());
        accountDomainValidator.validateUniqueNameAndType(repository, user.getId(), normalizedName, dto.getType(), null);

        Accounts newAccount = Accounts.builder()
                .id(ID.generate())
                .name(normalizedName)
                .type(dto.getType())
                .institution(normalizeInstitution(dto.getInstitution()))
                .currency(DEFAULT_CURRENCY)
                .currentBalance(dto.getInitialBalance())
                .initialBalance(dto.getInitialBalance())
                .calculateBalance(true)
                .enabled(true)
                .user(user)
                .createdAt(DateUtils.getEpochNow())
                .icon(resolveOrDefault(dto.getIcon(), DEFAULT_ICON))
                .color(resolveOrDefault(dto.getColor(), DEFAULT_COLOR))
                .isDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false)
                .build();

        Accounts savedAccount = repository.save(newAccount);
        log.info("Account created: accountId={}, userId={}, type={}", savedAccount.getId(), user.getId(), savedAccount.getType());
        return savedAccount;
    }

    /**
     * Busca uma conta ativa por ID sem validar propriedade para fluxos internos que fazem a própria validação.
     */
    @Override
    public Optional<Accounts> findById(UUID id) {
        return repository.findByIdAndNotDeleted(id);
    }

    /**
     * Busca uma conta ativa ou lança erro padronizado.
     */
    @Override
    public Accounts findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));
    }

    /**
     * Busca uma conta ativa garantindo que pertence ao usuário autenticado.
     */
    @Override
    public Accounts findMyAccountById(UUID id) {
        Accounts account = findByIdOrThrow(id);
        accountDomainValidator.validateOwner(account, SecurityContextUtils.getCurrentUser());
        return account;
    }

    /**
     * Lista as contas exibidas nas telas financeiras do usuário autenticado, sem contas espelho de cartão.
     */
    @Override
    public List<Accounts> listMyAccountsExceptCrediCard() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserId(user.getId());
    }

    /**
     * Atualiza dados cadastrais editáveis e preserva saldos movimentados por transações.
     */
    @Override
    public Accounts updateAccount(UUID id, AccountDTO dto) {
        Accounts account = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        accountDomainValidator.validateOwner(account, currentUser);

        String normalizedName = normalizeName(dto.getName());
        accountDomainValidator.validateUniqueNameAndType(repository, currentUser.getId(), normalizedName, dto.getType(), account.getId());

        if (dto.getName() != null) account.setName(normalizedName);
        if (dto.getType() != null) account.setType(dto.getType());
        if (dto.getInstitution() != null) account.setInstitution(normalizeInstitution(dto.getInstitution()));
        if (dto.getIcon() != null) account.setIcon(resolveOrDefault(dto.getIcon(), DEFAULT_ICON));
        if (dto.getColor() != null) account.setColor(resolveOrDefault(dto.getColor(), DEFAULT_COLOR));
        if (dto.getIsDefault() != null) account.setIsDefault(dto.getIsDefault());

        account.setUpdatedAt(DateUtils.getEpochNow());
        Accounts savedAccount = repository.save(account);
        log.info("Account updated: accountId={}, userId={}", savedAccount.getId(), currentUser.getId());
        return savedAccount;
    }

    /**
     * Remove logicamente uma conta do usuário autenticado quando ela não é a conta padrão.
     */
    @Override
    public void softDelete(UUID id) {
        Accounts account = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        accountDomainValidator.validateOwner(account, currentUser);
        accountDomainValidator.validateCanDelete(account);

        account.setDeletedAt(DateUtils.getEpochNow());
        repository.save(account);
        log.info("Account deleted: accountId={}, userId={}", account.getId(), currentUser.getId());
    }

    /**
     * Persiste alterações feitas por fluxos internos que já carregaram e validaram a conta.
     */
    @Override
    public Accounts update(Accounts accounts) {
        if (accounts.getId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CANT_UPDATE_ACCOUNT_NO_ID);
        }
        return repository.save(accounts);
    }

    /**
     * Salva uma conta montada por outros serviços, como criação automática no cadastro do usuário.
     */
    @Override
    public Accounts save(Accounts accounts) {
        return repository.save(accounts);
    }

    private String normalizeName(String name) {
        return name != null ? name.trim() : null;
    }

    private String normalizeInstitution(String institution) {
        return institution != null && !institution.trim().isEmpty() ? institution.trim() : "";
    }

    private String resolveOrDefault(String value, String defaultValue) {
        return value != null && !value.trim().isEmpty() ? value.trim() : defaultValue;
    }
}

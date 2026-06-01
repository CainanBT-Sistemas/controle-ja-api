package com.cainanbt.softwares.controleja.services.accounts;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;

import java.util.Optional;
import java.util.UUID;

/**
 * Centraliza as regras de integridade e propriedade das contas financeiras.
 */
public class AccountDomainValidator {

    /**
     * Garante que a conta pertence ao usuário autenticado antes de expor ou alterar dados.
     */
    public void validateOwner(Accounts account, Users currentUser) {
        if (account == null || account.getUser() == null || currentUser == null
                || !account.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }
    }

    /**
     * Bloqueia criação ou edição que gere outra conta ativa com o mesmo nome e tipo para o usuário.
     */
    public void validateUniqueNameAndType(
            AccountsRepository repository,
            UUID userId,
            String name,
            AccountType type,
            UUID currentAccountId) {
        Optional<Accounts> duplicatedAccount = repository.findByUserIdAndNameAndType(userId, name, type);
        boolean belongsToAnotherAccount = duplicatedAccount
                .map(account -> currentAccountId == null || !account.getId().equals(currentAccountId))
                .orElse(false);

        if (belongsToAnotherAccount) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NAME_ALREADY_EXIST);
        }
    }

    /**
     * Impede remoção da conta principal criada como padrão do usuário.
     */
    public void validateCanDelete(Accounts account) {
        if (Boolean.TRUE.equals(account.getIsDefault())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.CANT_DELETE_MAIN_ACCOUNT);
        }
        if (account.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }
    }
}

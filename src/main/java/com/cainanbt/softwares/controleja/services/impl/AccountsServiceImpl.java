package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;

    public AccountsServiceImpl(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
    }

    @Override
    @Transactional
    public Accounts createAccount(AccountDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        try {
            Accounts newAccount = Accounts.builder()
                    .id(ID.generate())
                    .name(dto.getName())
                    .type(dto.getType())
                    .institution(dto.getInstitution() != null ? dto.getInstitution() : "N/A") // Tratamento simples
                    .currency("BRL") // Hardcoded para MVP
                    .currentBalance(dto.getInitialBalance())
                    .initialBalance(dto.getInitialBalance())
                    .calculateBalance(true)
                    .enabled(true)
                    .user(user)
                    .createdAt(System.currentTimeMillis())
                    .build();

            return accountsRepository.save(newAccount);
        } catch (Exception e) {
            log.error("Erro ao criar conta para usuário {}: ", user.getId(), e);
            throw new BadRequestException("Falha ao criar conta", "Não foi possível criar a conta. Tente novamente.");
        }
    }

    @Override
    public List<Accounts> listMyAccounts() {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        return accountsRepository.findByUserId(user.getId());
    }

    @Override
    @Transactional
    public Accounts updateAccount(UUID accountId, AccountDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        try {
            Accounts account = accountsRepository.findById(accountId)
                    .orElseThrow(() -> new BadRequestException("Conta não encontrada", "A conta especificada não existe."));

            // Verificação de propriedade (segurança)
            if (!account.getUser().getId().equals(user.getId())) {
                log.warn("Tentativa de acesso não autorizado à conta {} por usuário {}", accountId, user.getId());
                throw new BadRequestException("Acesso Negado", "Você não tem permissão para modificar esta conta.");
            }

            // Atualizar apenas campos permitidos
            account.setName(dto.getName());
            account.setType(dto.getType());
            account.setInstitution(dto.getInstitution() != null ? dto.getInstitution() : account.getInstitution());

            return accountsRepository.save(account);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar conta {} para usuário {}: ", accountId, user.getId(), e);
            throw new BadRequestException("Falha ao atualizar conta", "Não foi possível atualizar a conta. Tente novamente.");
        }
    }

    @Override
    @Transactional
    public void deleteAccount(UUID accountId) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Erro de Segurança", "Usuário não autenticado"));

        try {
            Accounts account = accountsRepository.findById(accountId)
                    .orElseThrow(() -> new BadRequestException("Conta não encontrada", "A conta especificada não existe."));

            // Verificação de propriedade (segurança)
            if (!account.getUser().getId().equals(user.getId())) {
                log.warn("Tentativa de exclusão não autorizada da conta {} por usuário {}", accountId, user.getId());
                throw new BadRequestException("Acesso Negado", "Você não tem permissão para deletar esta conta.");
            }

            accountsRepository.delete(account);
            log.info("Conta {} deletada com sucesso pelo usuário {}", accountId, user.getId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao deletar conta {} para usuário {}: ", accountId, user.getId(), e);
            throw new BadRequestException("Falha ao deletar conta", "Não foi possível deletar a conta. Tente novamente.");
        }
    }
}
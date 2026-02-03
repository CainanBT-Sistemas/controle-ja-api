package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class CreditCardServiceImpl implements CreditCardService {
    private final CreditCardRepository creditCardRepository;

    public CreditCardServiceImpl(CreditCardRepository creditCardRepository) {
        this.creditCardRepository = creditCardRepository;
    }

    @Override
    @Transactional
    public CreditCard createCard(CreditCardDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Acesso Negado", "Usuário não autenticado"));

        try {
            long totalCards = creditCardRepository.countByUserId(user.getId());

            if (totalCards >= 2) {
                throw new BadRequestException("Limite Atingido", "Usuários Free só podem ter 2 cartões de crédito. Assine o Premium!");
            }

            CreditCard card = CreditCard.builder()
                    .id(ID.generate())
                    .name(dto.getName())
                    .totalLimit(dto.getLimit())
                    .currentLimit(dto.getLimit())
                    .closeDay(dto.getCloseDay())
                    .bestDay(dto.getBestDay())
                    .user(user)
                    .enabled(true)
                    .createdAt(System.currentTimeMillis())
                    .build();

            return creditCardRepository.save(card);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar cartão para usuário {}: ", user.getId(), e);
            throw new BadRequestException("Falha ao criar cartão", "Não foi possível criar o cartão de crédito. Tente novamente.");
        }
    }

    @Override
    @Transactional
    public CreditCard updateCard(UUID cardId, CreditCardDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Acesso Negado", "Usuário não autenticado"));

        try {
            CreditCard card = creditCardRepository.findById(cardId)
                    .orElseThrow(() -> new BadRequestException("Cartão não encontrado", "O cartão especificado não existe."));

            // Verificação de propriedade (segurança)
            if (!card.getUser().getId().equals(user.getId())) {
                log.warn("Tentativa de acesso não autorizado ao cartão {} por usuário {}", cardId, user.getId());
                throw new BadRequestException("Acesso Negado", "Você não tem permissão para modificar este cartão.");
            }

            // Atualizar campos
            card.setName(dto.getName());
            card.setTotalLimit(dto.getLimit());
            card.setCloseDay(dto.getCloseDay());
            card.setBestDay(dto.getBestDay());

            return creditCardRepository.save(card);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar cartão {} para usuário {}: ", cardId, user.getId(), e);
            throw new BadRequestException("Falha ao atualizar cartão", "Não foi possível atualizar o cartão. Tente novamente.");
        }
    }

    @Override
    @Transactional
    public void deleteCard(UUID cardId) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Acesso Negado", "Usuário não autenticado"));

        try {
            CreditCard card = creditCardRepository.findById(cardId)
                    .orElseThrow(() -> new BadRequestException("Cartão não encontrado", "O cartão especificado não existe."));

            // Verificação de propriedade (segurança)
            if (!card.getUser().getId().equals(user.getId())) {
                log.warn("Tentativa de exclusão não autorizada do cartão {} por usuário {}", cardId, user.getId());
                throw new BadRequestException("Acesso Negado", "Você não tem permissão para deletar este cartão.");
            }

            creditCardRepository.delete(card);
            log.info("Cartão {} deletado com sucesso pelo usuário {}", cardId, user.getId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao deletar cartão {} para usuário {}: ", cardId, user.getId(), e);
            throw new BadRequestException("Falha ao deletar cartão", "Não foi possível deletar o cartão. Tente novamente.");
        }
    }
}

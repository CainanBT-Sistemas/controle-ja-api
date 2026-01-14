package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;

@Service
public class CreditCardServiceImpl implements CreditCardService {
    private final CreditCardRepository creditCardRepository;

    public CreditCardServiceImpl(CreditCardRepository creditCardRepository) {
        this.creditCardRepository = creditCardRepository;
    }

    @Override
    public CreditCard createCard(CreditCardDTO dto) {
        Users user = SecurityContextUtils.getUserLogged()
                .orElseThrow(() -> new BadRequestException("Acesso Negado", "Usuário não autenticado"));

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
    }
}

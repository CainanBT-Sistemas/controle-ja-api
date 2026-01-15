package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;

import java.util.List;
import java.util.UUID;

public interface CreditCardService {
    CreditCard createCard(CreditCardDTO dto);

    List<CreditCard> listMyCards();

    CreditCard findByAccountId(UUID accountId);

    void updateLimit(CreditCard card);
}

package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;

import java.util.UUID;

public interface CreditCardService {
    CreditCard createCard(CreditCardDTO dto);

    CreditCard updateCard(UUID cardId, CreditCardDTO dto);

    void deleteCard(UUID cardId);
}

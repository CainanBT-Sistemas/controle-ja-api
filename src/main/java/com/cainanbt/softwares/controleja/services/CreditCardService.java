package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.entities.CreditCard;

public interface CreditCardService {
    CreditCard createCard(CreditCardDTO dto);
}

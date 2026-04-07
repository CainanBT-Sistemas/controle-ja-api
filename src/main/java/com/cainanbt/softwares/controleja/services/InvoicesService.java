package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.Invoices;

import java.util.Optional;
import java.util.UUID;

public interface InvoicesService {
    Invoices save(Invoices invoice);

    Optional<Invoices> findById(UUID id);

    Invoices findByIdOrThrow(UUID id);

    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);
}

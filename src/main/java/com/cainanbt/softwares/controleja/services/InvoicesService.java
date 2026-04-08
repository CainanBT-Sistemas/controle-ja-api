package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.Invoices;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoicesService {
    Invoices save(Invoices invoice);

    List<Invoices> saveAll(List<Invoices> invoices);

    Optional<Invoices> findById(UUID id);

    Invoices findByIdOrThrow(UUID id);

    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);
}

package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.InvoicesRepository;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cainanbt.softwares.controleja.utils.ConstsMessages.INVOICE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InvoicesServiceImpl implements InvoicesService {
    private final InvoicesRepository repository;

    @Override
    public Invoices save(Invoices invoice) {
        return repository.save(invoice);
    }

    @Override
    public List<Invoices> saveAll(List<Invoices> invoices) {
        return repository.saveAll(invoices);
    }

    @Override
    public Optional<Invoices> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Invoices findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, INVOICE_NOT_FOUND));
    }

    @Override
    public Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year) {
        return repository.findByCreditCardIdAndMonthAndYear(creditCardId, month, year);
    }
}

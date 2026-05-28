package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionProcessorFactory {
    private final List<TransactionProcessor> processors;

    public TransactionProcessor getProcessor(TransactionDTO dto, Accounts account) {
        return processors.stream()
                .filter(processor -> processor.supports(dto, account))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, "Não foi encontrado um processador válido para este tipo de transação."));
    }
}

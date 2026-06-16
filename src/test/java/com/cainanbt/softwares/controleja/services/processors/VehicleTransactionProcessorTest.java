package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleConsumptionCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class VehicleTransactionProcessorTest {

    @Mock
    private VehicleService vehicleService;
    @Mock
    private GasStationService gasStationService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private VehicleConsumptionCalculator vehicleConsumptionCalculator;

    @InjectMocks
    private VehicleTransactionProcessor processor;

    @Test
    void shouldIgnoreVehicleFieldsFromInvoicePayment() {
        TransactionDTO dto = new TransactionDTO();
        dto.setType(TransactionType.PAGAMENTO_FATURA);
        dto.setVehicleId(UUID.randomUUID());
        dto.setGasStationId(UUID.randomUUID());
        dto.setCurrentOdometer(new BigDecimal("180400.0"));

        Transactions.TransactionsBuilder builder = Transactions.builder();
        processor.apply(dto, builder, Users.builder().id(UUID.randomUUID()).build());
        Transactions transaction = builder.build();

        assertNull(transaction.getVehicle());
        assertNull(transaction.getGasStation());
        assertNull(transaction.getCurrentOdometer());
        verifyNoInteractions(vehicleService, gasStationService, transactionRepository, vehicleConsumptionCalculator);
    }
}

package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleConsumptionCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleTransactionProcessorTest {

    @Mock
    private VehicleService vehicleService;
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
        dto.setCurrentOdometer(new BigDecimal("180400.0"));

        Transactions.TransactionsBuilder builder = Transactions.builder();
        processor.apply(dto, builder, Users.builder().id(UUID.randomUUID()).build());
        Transactions transaction = builder.build();

        assertNull(transaction.getVehicle());
        assertNull(transaction.getGasStation());
        assertNull(transaction.getCurrentOdometer());
        verifyNoInteractions(vehicleService, transactionRepository, vehicleConsumptionCalculator);
    }

    @Test
    void shouldKeepVehicleExpenseOutOfOdometerTimelineWhenItIsNotRefuel() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        TransactionDTO dto = new TransactionDTO();
        dto.setType(TransactionType.DESPESA);
        dto.setVehicleId(vehicleId);
        dto.setCurrentOdometer(new BigDecimal("180400.0"));
        Vehicle vehicle = Vehicle.builder().id(vehicleId).user(Users.builder().id(userId).build()).build();
        when(vehicleService.findById(vehicleId)).thenReturn(vehicle);

        Transactions.TransactionsBuilder builder = Transactions.builder();
        processor.apply(dto, builder, Users.builder().id(userId).build());
        Transactions transaction = builder.build();

        assertEquals(vehicle, transaction.getVehicle());
        assertNull(transaction.getCurrentOdometer());
        assertNull(transaction.getLiters());
        assertNull(transaction.getFuelType());
        assertFalse(transaction.getFullTank());
    }

    @Test
    void shouldApplyFullTankOnlyForRefuel() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        TransactionDTO dto = new TransactionDTO();
        dto.setType(TransactionType.DESPESA);
        dto.setVehicleId(vehicleId);
        dto.setCurrentOdometer(new BigDecimal("180400.0"));
        dto.setLiters(40.0);
        dto.setFullTank(true);
        Vehicle vehicle = Vehicle.builder().id(vehicleId).user(Users.builder().id(userId).build()).build();
        when(vehicleService.findById(vehicleId)).thenReturn(vehicle);

        Transactions.TransactionsBuilder builder = Transactions.builder();
        processor.apply(dto, builder, Users.builder().id(userId).build());
        Transactions transaction = builder.build();

        assertEquals(new BigDecimal("180400.0"), transaction.getCurrentOdometer());
        assertEquals(40.0, transaction.getLiters());
        assertNull(transaction.getFuelType());
        assertTrue(transaction.getFullTank());
    }

    @Test
    void shouldKeepOptionalFuelTypeWhenInformedForRefuel() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        TransactionDTO dto = new TransactionDTO();
        dto.setType(TransactionType.DESPESA);
        dto.setVehicleId(vehicleId);
        dto.setCurrentOdometer(new BigDecimal("180400.0"));
        dto.setLiters(40.0);
        dto.setFuelType(FuelType.GASOLINA);
        dto.setFullTank(true);
        Vehicle vehicle = Vehicle.builder().id(vehicleId).user(Users.builder().id(userId).build()).build();
        when(vehicleService.findById(vehicleId)).thenReturn(vehicle);

        Transactions.TransactionsBuilder builder = Transactions.builder();
        processor.apply(dto, builder, Users.builder().id(userId).build());
        Transactions transaction = builder.build();

        assertEquals(FuelType.GASOLINA, transaction.getFuelType());
    }

    @Test
    void shouldRejectFullTankWhenTransactionIsNotRefuel() {
        TransactionDTO dto = new TransactionDTO();
        dto.setType(TransactionType.DESPESA);
        dto.setVehicleId(UUID.randomUUID());
        dto.setFullTank(true);

        assertThrows(
                BadRequestException.class,
                () -> processor.apply(dto, Transactions.builder(), Users.builder().id(UUID.randomUUID()).build())
        );
    }
}

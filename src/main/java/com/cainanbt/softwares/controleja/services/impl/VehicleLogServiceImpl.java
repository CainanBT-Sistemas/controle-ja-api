package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleLogService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleDomainValidator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleLogServiceImpl implements VehicleLogService {
    private final VehicleDomainValidator vehicleDomainValidator = new VehicleDomainValidator();

    private final VehicleLogRepository repository;
    private final TransactionRepository transactionRepository;
    private final VehicleService vehicleService;

    /**
     * Cria uma leitura de diário de bordo e atualiza o odômetro principal do veículo.
     */
    @Override
    @Transactional
    public VehicleLog createLog(VehicleLogDTO dto) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        Vehicle vehicle = vehicleService.findByIdOrThrow(dto.getVehicleId());
        vehicleDomainValidator.validateOwner(vehicle, currentUser);
        vehicleDomainValidator.validateLogRequest(vehicle, dto);

        VehicleLog vehicleLog = VehicleLog.builder()
                .id(ID.generate())
                .date(dto.getDate())
                .odometerReading(dto.getOdometerReading())
                .dashboardKml(dto.getDashboardKml())
                .drivingPredominance(dto.getDrivingPredominance())
                .vehicle(vehicle)
                .user(currentUser)
                .createdAt(DateUtils.getEpochNow())
                .build();

        vehicleService.updateOdometer(vehicle, dto.getOdometerReading());

        VehicleLog saved = repository.save(vehicleLog);
        log.info("Vehicle log created: logId={}, vehicleId={}, odometer={}", saved.getId(), vehicle.getId(), saved.getOdometerReading());
        return saved;
    }

    /**
     * Lista todas as leituras de diário de bordo do veículo autenticado.
     */
    @Override
    public List<VehicleLog> listLogsByVehicle(UUID vehicleId) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);
        vehicleDomainValidator.validateOwner(vehicle, currentUser);

        return repository.findByVehicleIdOrderByDateDesc(vehicleId);
    }

    /**
     * Lista leituras do veículo, filtrando por período quando o frontend informar datas.
     */
    @Override
    public List<VehicleLog> listLogsByVehicle(UUID vehicleId, Long start, Long end) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);
        vehicleDomainValidator.validateOwner(vehicle, currentUser);

        if (start != null && end != null) {
            return repository.findByVehicleIdAndDateBetweenOrderByDateDesc(vehicleId, start, end);
        }
        return repository.findByVehicleIdOrderByDateDesc(vehicleId);
    }

    /**
     * Exclui apenas a última leitura do diário e recalcula o odômetro principal do veículo.
     */
    @Override
    @Transactional
    public void deleteLastLog(UUID id) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        VehicleLog logToDelete = repository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Diário de bordo não encontrado."));

        Vehicle vehicle = logToDelete.getVehicle();
        vehicleDomainValidator.validateOwner(vehicle, currentUser);
        validateCanDeleteLog(logToDelete);

        repository.delete(logToDelete);
        recalculateVehicleCurrentOdometer(vehicle);
        log.info("Vehicle log deleted: logId={}, vehicleId={}", id, vehicle.getId());
    }

    /**
     * Garante que somente a última leitura cronológica do diário seja excluída.
     */
    private void validateCanDeleteLog(VehicleLog logToDelete) {
        Vehicle vehicle = logToDelete.getVehicle();
        VehicleLog lastLog = repository.findFirstByVehicleIdOrderByDateDescCreatedAtDesc(vehicle.getId())
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Diário de bordo não encontrado."));
        if (!lastLog.getId().equals(logToDelete.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Apenas o último lançamento do diário de bordo pode ser excluído.");
        }
    }

    /**
     * Recalcula o odômetro atual considerando transações veiculares, diário restante e odômetro inicial.
     */
    private void recalculateVehicleCurrentOdometer(Vehicle vehicle) {
        BigDecimal recalculatedOdometer = vehicle.getInitialOdometer() != null
                ? vehicle.getInitialOdometer()
                : BigDecimal.ZERO;
        BigDecimal maxTransactionOdometer = transactionRepository.findMaxCurrentOdometerByVehicleId(vehicle.getId());
        BigDecimal maxLogOdometer = repository.findMaxOdometerReadingByVehicleId(vehicle.getId());

        if (maxTransactionOdometer != null && maxTransactionOdometer.compareTo(recalculatedOdometer) > 0) {
            recalculatedOdometer = maxTransactionOdometer;
        }
        if (maxLogOdometer != null && maxLogOdometer.compareTo(recalculatedOdometer) > 0) {
            recalculatedOdometer = maxLogOdometer;
        }

        vehicleService.setCurrentOdometer(vehicle, recalculatedOdometer);
    }
}

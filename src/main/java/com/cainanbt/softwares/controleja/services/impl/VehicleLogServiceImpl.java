package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleLogService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleDomainValidator;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleLogServiceImpl implements VehicleLogService {
    private final VehicleDomainValidator vehicleDomainValidator = new VehicleDomainValidator();

    private final VehicleLogRepository repository;
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
}

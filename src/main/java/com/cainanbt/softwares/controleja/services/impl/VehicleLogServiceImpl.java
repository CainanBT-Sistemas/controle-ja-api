package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleLogService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleLogServiceImpl implements VehicleLogService {

    private final VehicleLogRepository repository;
    private final VehicleService vehicleService;

    @Override
    @Transactional
    public VehicleLog createLog(VehicleLogDTO dto) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        // 1. Busca o veículo e valida se pertence ao usuário
        Vehicle vehicle = vehicleService.findByIdOrThrow(dto.getVehicleId());

        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        // 2. Cria o registro do Diário de Bordo
        VehicleLog log = VehicleLog.builder()
                .id(ID.generate())
                .date(dto.getDate())
                .odometerReading(dto.getOdometerReading())
                .dashboardKml(dto.getDashboardKml())
                .vehicle(vehicle)
                .user(currentUser)
                .createdAt(DateUtils.getEpochNow())
                .build();

        // 3. Atualiza a quilometragem principal do veículo
        // (O seu método updateOdometer lá no VehicleService já tem a proteção para não deixar o KM diminuir, o que é perfeito aqui)
        vehicleService.updateOdometer(vehicle, dto.getOdometerReading());

        return repository.save(log);
    }

    @Override
    public List<VehicleLog> listLogsByVehicle(UUID vehicleId) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);

        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        return repository.findByVehicleIdOrderByDateDesc(vehicleId);
    }

    @Override
    public List<VehicleLog> listLogsByVehicle(UUID vehicleId, Long start, Long end) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);

        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        // Se o front-end mandar as datas, filtra. Se não mandar, traz tudo.
        if (start != null && end != null) {
            return repository.findByVehicleIdAndDateBetweenOrderByDateDesc(vehicleId, start, end);
        }
        return repository.findByVehicleIdOrderByDateDesc(vehicleId);
    }
}
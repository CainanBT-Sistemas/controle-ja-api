package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.VehicleLog;

import java.util.List;
import java.util.UUID;

public interface VehicleLogService {
    VehicleLog createLog(VehicleLogDTO dto);

    List<VehicleLog> listLogsByVehicle(UUID vehicleId);

    List<VehicleLog> listLogsByVehicle(UUID vehicleId, Long start, Long end);
}
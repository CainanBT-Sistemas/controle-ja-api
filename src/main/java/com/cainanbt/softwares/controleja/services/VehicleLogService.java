package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.VehicleLog;

import java.util.List;
import java.util.UUID;

public interface VehicleLogService {
    /**
     * Cria uma leitura de diário de bordo para o veículo informado.
     */
    VehicleLog createLog(VehicleLogDTO dto);

    /**
     * Lista todas as leituras do veículo autenticado.
     */
    List<VehicleLog> listLogsByVehicle(UUID vehicleId);

    /**
     * Lista leituras do veículo autenticado em um período opcional.
     */
    List<VehicleLog> listLogsByVehicle(UUID vehicleId, Long start, Long end);
}

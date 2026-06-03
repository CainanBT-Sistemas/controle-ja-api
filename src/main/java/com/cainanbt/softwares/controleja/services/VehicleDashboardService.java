package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Vehicle;

import java.util.UUID;

public interface VehicleDashboardService {
    /**
     * Monta o dashboard a partir do veículo já carregado e autorizado pelo fluxo chamador.
     */
    VehicleDashboardDTO getDashboard(Vehicle vehicle, Long startOfMonth, Long endOfMonth);

    /**
     * Monta o dashboard carregando o veículo pelo identificador informado.
     */
    VehicleDashboardDTO getDashboard(UUID vehicleId, Long startOfMonth, Long endOfMonth);
}

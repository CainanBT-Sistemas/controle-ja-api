package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;

import java.util.UUID;

public interface VehicleDashboardService {
    VehicleDashboardDTO getDashboard(UUID vehicleId, Long startOfMonth, Long endOfMonth);
}
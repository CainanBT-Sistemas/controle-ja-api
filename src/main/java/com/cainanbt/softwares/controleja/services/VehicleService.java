package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface VehicleService {
    Vehicle createVehicle(VehicleDTO dto);

    List<Vehicle> listMyVehicles();

    Vehicle findById(UUID id);

    void updateOdometer(Vehicle vehicle, java.math.BigDecimal newOdometer);

    Double processRefuel(Vehicle vehicle, BigDecimal newOdometer, Double liters, FuelType fuelType);

}

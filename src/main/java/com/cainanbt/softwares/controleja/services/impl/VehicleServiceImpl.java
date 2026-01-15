package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleServiceImpl implements VehicleService {
    private final VehicleRepository repository;

    public VehicleServiceImpl(VehicleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Vehicle createVehicle(VehicleDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        Vehicle vehicle = Vehicle.builder()
                .id(ID.generate())
                .name(dto.getName())
                .brand(dto.getBrand())
                .model(dto.getModel())
                .year(dto.getYear())
                .plate(dto.getPlate())
                .currentOdometer(dto.getCurrentOdometer())
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();
        return repository.save(vehicle);
    }

    @Override
    public List<Vehicle> listMyVehicles() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdAndDeletedAtIsNull(user.getId());
    }

    @Override
    public Vehicle findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BadRequestException("Erro", "Veículo não encontrado"));
    }

    @Override
    public void updateOdometer(Vehicle vehicle, BigDecimal newOdometer) {
        if (newOdometer != null && newOdometer.compareTo(vehicle.getCurrentOdometer()) > 0) {
            vehicle.setCurrentOdometer(newOdometer);
            repository.save(vehicle);
        }
    }
}

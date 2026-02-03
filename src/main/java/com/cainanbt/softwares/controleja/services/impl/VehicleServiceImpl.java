package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
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
        return repository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BadRequestException("Erro", "Veículo não encontrado"));
    }

    @Override
    public void updateOdometer(Vehicle vehicle, BigDecimal newOdometer) {
        if (newOdometer != null && newOdometer.compareTo(vehicle.getCurrentOdometer()) > 0) {
            vehicle.setCurrentOdometer(newOdometer);
            repository.save(vehicle);
        }
    }

    @Override
    public Double processRefuel(Vehicle vehicle, BigDecimal newOdometer, Double liters, FuelType fuelType) {
        if (newOdometer == null || newOdometer.compareTo(vehicle.getCurrentOdometer()) <= 0) {
            return null; // Não dá pra calcular
        }
        if (liters == null || liters <= 0) {
            vehicle.setCurrentOdometer(newOdometer); // Só atualiza KM
            repository.save(vehicle);
            return null;
        }

        BigDecimal distance = newOdometer.subtract(vehicle.getCurrentOdometer());
        double km = distance.doubleValue();
        double consumption = km / liters;

        if (fuelType == FuelType.GASOLINA) {
            vehicle.setAvgKmPerLiterGasoline(calculateRollingAverage(vehicle.getAvgKmPerLiterGasoline(), consumption));
        } else if (fuelType == FuelType.ETANOL) {
            vehicle.setAvgKmPerLiterEthanol(calculateRollingAverage(vehicle.getAvgKmPerLiterEthanol(), consumption));
        }
        vehicle.setCurrentOdometer(newOdometer);
        repository.save(vehicle);
        return consumption;
    }

    private Double calculateRollingAverage(Double currentAverage, double newConsumption) {
        if (currentAverage == null || currentAverage == 0) {
            return newConsumption;
        }
        return (currentAverage + newConsumption) / 2;
    }
    
    @Override
    public Vehicle findByIdOrThrow(UUID id) {
        return repository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.VEHICLE_NOT_FOUND,
                    "Veículo com ID " + id + " não encontrado ou já foi excluído"));
    }
    
    @Override
    public Vehicle updateVehicle(UUID id, VehicleDTO dto) {
        Vehicle vehicle = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        
        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para alterar este veículo");
        }
        
        if (dto.getName() != null) {
            vehicle.setName(dto.getName());
        }
        if (dto.getBrand() != null) {
            vehicle.setBrand(dto.getBrand());
        }
        if (dto.getModel() != null) {
            vehicle.setModel(dto.getModel());
        }
        if (dto.getYear() != null) {
            vehicle.setYear(dto.getYear());
        }
        if (dto.getPlate() != null) {
            vehicle.setPlate(dto.getPlate());
        }
        if (dto.getCurrentOdometer() != null) {
            vehicle.setCurrentOdometer(dto.getCurrentOdometer());
        }
        
        vehicle.setUpdatedAt(System.currentTimeMillis());
        
        return repository.save(vehicle);
    }
    
    @Override
    public void softDelete(UUID id) {
        Vehicle vehicle = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        
        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para excluir este veículo");
        }
        
        if (vehicle.getDeletedAt() != null) {
            throw new BadRequestException("Erro", ConstsMessages.ENTITY_ALREADY_DELETED);
        }
        
        vehicle.setDeletedAt(System.currentTimeMillis());
        repository.save(vehicle);
    }
}

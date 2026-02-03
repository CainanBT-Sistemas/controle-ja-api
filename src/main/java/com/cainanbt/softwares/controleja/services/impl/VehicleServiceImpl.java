package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.exceptions.models.InternalServerException;
import com.cainanbt.softwares.controleja.exceptions.models.NotFoundException;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Vehicle createVehicle(VehicleDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        
        try {
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
        } catch (Exception e) {
            throw new InternalServerException("Erro ao criar veículo", "Não foi possível criar o veículo. Tente novamente.", e);
        }
    }

    @Override
    public List<Vehicle> listMyVehicles() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdAndDeletedAtIsNull(user.getId());
    }

    @Override
    public Vehicle findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado", "O veículo especificado não existe."));
    }

    @Override
    @Transactional
    public void updateOdometer(Vehicle vehicle, BigDecimal newOdometer) {
        if (newOdometer != null && newOdometer.compareTo(vehicle.getCurrentOdometer()) > 0) {
            vehicle.setCurrentOdometer(newOdometer);
            try {
                repository.save(vehicle);
            } catch (Exception e) {
                throw new InternalServerException("Erro ao atualizar odômetro", "Não foi possível atualizar o odômetro. Tente novamente.", e);
            }
        }
    }

    @Override
    @Transactional
    public Double processRefuel(Vehicle vehicle, BigDecimal newOdometer, Double liters, FuelType fuelType) {
        if (newOdometer == null || newOdometer.compareTo(vehicle.getCurrentOdometer()) <= 0) {
            return null; // Não dá pra calcular
        }
        if (liters == null || liters <= 0) {
            vehicle.setCurrentOdometer(newOdometer); // Só atualiza KM
            try {
                repository.save(vehicle);
            } catch (Exception e) {
                throw new InternalServerException("Erro ao processar abastecimento", "Não foi possível processar o abastecimento. Tente novamente.", e);
            }
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
        
        try {
            repository.save(vehicle);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao processar abastecimento", "Não foi possível processar o abastecimento. Tente novamente.", e);
        }
        return consumption;
    }

    private Double calculateRollingAverage(Double currentAverage, double newConsumption) {
        if (currentAverage == null || currentAverage == 0) {
            return newConsumption;
        }
        return (currentAverage + newConsumption) / 2;
    }

    @Override
    @Transactional
    public void deleteVehicle(UUID id) {
        Users user = SecurityContextUtils.getCurrentUser();
        
        Vehicle vehicle = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado", "O veículo especificado não existe."));
        
        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Acesso negado", "Você não tem permissão para deletar este veículo.");
        }
        
        try {
            vehicle.setDeletedAt(System.currentTimeMillis());
            repository.save(vehicle);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao deletar veículo", "Não foi possível deletar o veículo. Tente novamente.", e);
        }
    }
}

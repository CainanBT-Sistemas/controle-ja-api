package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleDomainValidator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class VehicleServiceImpl implements VehicleService {
    private final VehicleDomainValidator vehicleDomainValidator = new VehicleDomainValidator();
    private final VehicleRepository repository;

    /**
     * Cria um veículo do usuário autenticado usando o odômetro informado como leitura inicial e atual.
     */
    @Override
    public Vehicle createVehicle(VehicleDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        vehicleDomainValidator.validateInitialOdometer(dto.getCurrentOdometer());

        Vehicle vehicle = Vehicle.builder()
                .id(ID.generate())
                .name(dto.getName())
                .brand(dto.getBrand())
                .model(dto.getModel())
                .year(dto.getYear())
                .plate(dto.getPlate())
                .currentOdometer(dto.getCurrentOdometer())
                .initialOdometer(dto.getCurrentOdometer())
                .tankCapacity(dto.getTankCapacity())
                .user(user)
                .createdAt(DateUtils.getEpochNow())
                .build();

        return repository.save(vehicle);
    }

    /**
     * Lista os veículos ativos do usuário autenticado.
     */
    @Override
    public List<Vehicle> listMyVehicles() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdAndDeletedAtIsNull(user.getId());
    }

    /**
     * Busca veículo ativo por id sem validar posse; usado por fluxos que validam o usuário no próprio contexto.
     */
    @Override
    public Vehicle findById(UUID id) {
        return repository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.VEHICLE_NOT_FOUND));
    }

    /**
     * Busca veículo ativo e valida que pertence ao usuário autenticado.
     */
    @Override
    public Vehicle findMyVehicleById(UUID id) {
        Vehicle vehicle = findByIdOrThrow(id);
        vehicleDomainValidator.validateOwner(vehicle, SecurityContextUtils.getCurrentUser());
        return vehicle;
    }

    /**
     * Define a leitura atual do veículo após validação básica de valor.
     */
    @Override
    public void setCurrentOdometer(Vehicle vehicle, BigDecimal newOdometer) {
        vehicleDomainValidator.validateInitialOdometer(newOdometer);
        if (newOdometer != null && newOdometer.compareTo(BigDecimal.ZERO) > 0) {
            vehicle.setCurrentOdometer(newOdometer);
            repository.save(vehicle);
        }
    }

    /**
     * Busca veículo ativo por id e retorna erro de domínio quando não existir.
     */
    @Override
    public Vehicle findByIdOrThrow(UUID id) {
        return repository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.VEHICLE_NOT_FOUND));
    }

    /**
     * Atualiza apenas o apelido do veículo para preservar dados estruturais e histórico de odômetro.
     */
    @Override
    public Vehicle updateVehicle(UUID id, VehicleDTO dto) {
        Vehicle vehicle = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        vehicleDomainValidator.validateOwner(vehicle, currentUser);

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            vehicle.setName(dto.getName());
        }

        vehicle.setUpdatedAt(DateUtils.getEpochNow());
        return repository.save(vehicle);
    }

    /**
     * Remove logicamente um veículo do usuário autenticado.
     */
    @Override
    public void softDelete(UUID id) {
        Vehicle vehicle = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        vehicleDomainValidator.validateOwner(vehicle, currentUser);

        if (vehicle.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        vehicle.setDeletedAt(DateUtils.getEpochNow());
        repository.save(vehicle);
    }
}

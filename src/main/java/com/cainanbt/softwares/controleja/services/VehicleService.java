package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Vehicle;

import java.util.List;
import java.util.UUID;

public interface VehicleService {
    /**
     * Cria um veículo para o usuário autenticado.
     */
    Vehicle createVehicle(VehicleDTO dto);

    /**
     * Lista veículos ativos do usuário autenticado.
     */
    List<Vehicle> listMyVehicles();

    /**
     * Busca veículo ativo por id para fluxos internos que fazem validação de posse no contexto chamador.
     */
    Vehicle findById(UUID id);

    /**
     * Busca veículo ativo do usuário autenticado.
     */
    Vehicle findMyVehicleById(UUID id);

    /**
     * Define o odômetro atual após validação de valor.
     */
    void setCurrentOdometer(Vehicle vehicle, java.math.BigDecimal newOdometer);

    /**
     * Busca veículo ativo por id e falha com erro de domínio quando não existir.
     */
    Vehicle findByIdOrThrow(UUID id);

    /**
     * Atualiza campos editáveis do veículo.
     */
    Vehicle updateVehicle(UUID id, VehicleDTO dto);

    /**
     * Remove logicamente um veículo do usuário autenticado.
     */
    void softDelete(UUID id);

}

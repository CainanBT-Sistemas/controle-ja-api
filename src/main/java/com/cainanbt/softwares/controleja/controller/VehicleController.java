package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.dtos.responses.VehicleResponseDTO;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.services.VehicleDashboardService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    private final VehicleDashboardService dashboardService;

    public VehicleController(VehicleService vehicleService, VehicleDashboardService dashboardService) {
        this.vehicleService = vehicleService;
        this.dashboardService = dashboardService;
    }

    /**
     * Cadastra um veículo para o usuário autenticado.
     */
    @PostMapping
    public ResponseEntity<VehicleResponseDTO> create(@RequestBody @Valid VehicleDTO dto) {
        return ResponseEntity.ok(VehicleResponseDTO.toDTO(vehicleService.createVehicle(dto)));
    }

    /**
     * Lista os veículos ativos do usuário autenticado.
     */
    @GetMapping
    public ResponseEntity<List<VehicleResponseDTO>> listAll() {
        return ResponseEntity.ok(vehicleService.listMyVehicles().stream().map(VehicleResponseDTO::toDTO).toList());
    }

    /**
     * Consulta um veículo ativo do usuário autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(VehicleResponseDTO.toDTO(vehicleService.findMyVehicleById(id)));
    }

    /**
     * Atualiza campos editáveis do veículo.
     */
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid VehicleDTO dto) {
        return ResponseEntity.ok(VehicleResponseDTO.toDTO(vehicleService.updateVehicle(id, dto)));
    }

    /**
     * Remove logicamente o veículo do usuário autenticado.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        vehicleService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }

    /**
     * Retorna indicadores financeiros e de consumo do veículo no período informado.
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<VehicleDashboardDTO> getDashboard(
            @PathVariable UUID id,
            @RequestParam Long start,
            @RequestParam Long end) {
        Vehicle vehicle = vehicleService.findMyVehicleById(id);
        return ResponseEntity.ok(dashboardService.getDashboard(vehicle, start, end));
    }

}

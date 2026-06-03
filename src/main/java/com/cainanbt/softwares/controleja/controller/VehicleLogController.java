package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.dtos.responses.VehicleLogResponseDTO;
import com.cainanbt.softwares.controleja.services.VehicleLogService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/vehicles/logs")
@RequiredArgsConstructor
public class VehicleLogController {
    private final VehicleLogService service;

    /**
     * Cria uma leitura de diário de bordo para o veículo autenticado.
     */
    @PostMapping
    public ResponseEntity<VehicleLogResponseDTO> create(@RequestBody @Valid VehicleLogDTO dto) {
        return ResponseEntity.ok(VehicleLogResponseDTO.toDTO(service.createLog(dto)));
    }

    /**
     * Lista leituras do diário de bordo, com filtro opcional por período.
     */
    @GetMapping("/{vehicleId}")
    public ResponseEntity<List<VehicleLogResponseDTO>> listByVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end) {
        return ResponseEntity.ok(service.listLogsByVehicle(vehicleId, start, end).stream().map(VehicleLogResponseDTO::toDTO).toList());
    }

    /**
     * Exclui somente o último lançamento do diário de bordo.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
        service.deleteLastLog(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }
}

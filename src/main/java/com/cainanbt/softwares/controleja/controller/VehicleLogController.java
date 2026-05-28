package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.dtos.responses.VehicleLogResponseDTO;
import com.cainanbt.softwares.controleja.services.VehicleLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/vehicles/logs")
@RequiredArgsConstructor
public class VehicleLogController {
    private final VehicleLogService service;

    @PostMapping
    public ResponseEntity<VehicleLogResponseDTO> create(@RequestBody @Valid VehicleLogDTO dto) {
        return ResponseEntity.ok(VehicleLogResponseDTO.toDTO(service.createLog(dto)));
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<List<VehicleLogResponseDTO>> listByVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end) {
        return ResponseEntity.ok(service.listLogsByVehicle(vehicleId, start, end).stream().map(VehicleLogResponseDTO::toDTO).toList());
    }
}
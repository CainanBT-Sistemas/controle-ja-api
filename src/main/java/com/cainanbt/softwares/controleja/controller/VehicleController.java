package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.dtos.responses.VehicleResponseDTO;
import com.cainanbt.softwares.controleja.services.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("controle_ja_api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponseDTO> create(@RequestBody @Valid VehicleDTO dto) {
        return ResponseEntity.ok(VehicleResponseDTO.toDTO(vehicleService.createVehicle(dto)));
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponseDTO>> listAll() {
        return ResponseEntity.ok(vehicleService.listMyVehicles().stream().map(VehicleResponseDTO::toDTO).toList());
    }
}
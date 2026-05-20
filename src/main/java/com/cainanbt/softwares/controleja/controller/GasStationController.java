package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.GasStationResponseDTO;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("controle_ja_api/v1/gas-stations")
@RequiredArgsConstructor
public class GasStationController {
    private final GasStationService gasStationService;
    private final GasStationRankingService rankingService;

    @PostMapping
    public ResponseEntity<GasStationResponseDTO> create(@RequestBody @Valid GasStationDTO dto) {
        return ResponseEntity.ok(GasStationResponseDTO.toDTO(gasStationService.createGasStation(dto)));
    }

    @GetMapping
    public ResponseEntity<List<GasStationResponseDTO>> listAll() {
        return ResponseEntity.ok(gasStationService.listMyGasStations().stream()
                .map(GasStationResponseDTO::toDTO)
                .collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GasStationResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid GasStationDTO dto) {
        return ResponseEntity.ok(GasStationResponseDTO.toDTO(gasStationService.updateGasStation(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        gasStationService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<GasStationRankingResponseDTO>> getRanking() {
        return ResponseEntity.ok(rankingService.getMyRankings());
    }
}

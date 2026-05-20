package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;

import java.util.List;
import java.util.UUID;

public interface GasStationService {
    GasStation createGasStation(GasStationDTO dto);

    List<GasStation> listMyGasStations();

    GasStation updateGasStation(UUID id, GasStationDTO dto);

    void softDelete(UUID id);

    GasStation findByIdOrThrow(UUID id);
}

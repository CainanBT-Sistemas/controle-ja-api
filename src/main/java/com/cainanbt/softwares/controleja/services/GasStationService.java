package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;

import java.util.List;
import java.util.UUID;

public interface GasStationService {
    /**
     * Cria posto de combustível para o usuário autenticado.
     */
    GasStation createGasStation(GasStationDTO dto);

    /**
     * Lista postos ativos do usuário autenticado.
     */
    List<GasStation> listMyGasStations();

    /**
     * Busca posto ativo garantindo que pertence ao usuário autenticado.
     */
    GasStation findMyGasStationById(UUID id);

    /**
     * Atualiza posto do usuário autenticado.
     */
    GasStation updateGasStation(UUID id, GasStationDTO dto);

    /**
     * Remove logicamente posto do usuário autenticado.
     */
    void softDelete(UUID id);

    /**
     * Busca posto ativo por id sem aplicar regra de propriedade.
     */
    GasStation findByIdOrThrow(UUID id);
}

package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;

import java.util.List;

public interface GasStationRankingService {
    void updateRanking(Transactions tx);

    List<GasStationRankingResponseDTO> getMyRankings();
}

package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;

import java.util.List;

public interface GasStationRankingService {
    /**
     * Atualiza o ranking do posto quando uma transação de abastecimento possui dados confiáveis.
     */
    void updateRanking(Transactions tx);

    /**
     * Lista rankings dos postos do usuário autenticado.
     */
    List<GasStationRankingResponseDTO> getMyRankings();
}

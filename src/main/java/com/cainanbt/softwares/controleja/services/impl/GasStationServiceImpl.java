package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.GasStationRepository;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.services.gasstations.GasStationDomainValidator;
import com.cainanbt.softwares.controleja.services.gasstations.GasStationFactory;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GasStationServiceImpl implements GasStationService {
    private final GasStationDomainValidator gasStationDomainValidator = new GasStationDomainValidator();
    private final GasStationFactory gasStationFactory = new GasStationFactory();

    private final GasStationRepository repository;

    /**
     * Cria um posto de combustível para o usuário autenticado.
     */
    @Override
    @Transactional
    public GasStation createGasStation(GasStationDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        GasStation station = repository.save(gasStationFactory.create(dto, user, DateUtils.getEpochNow()));
        log.info("Gas station created: stationId={}", station.getId());
        return station;
    }

    /**
     * Lista somente postos ativos do usuário autenticado.
     */
    @Override
    public List<GasStation> listMyGasStations() {
        return repository.findByUserIdAndDeletedAtIsNull(SecurityContextUtils.getCurrentUser().getId());
    }

    /**
     * Busca posto ativo por id ou lança erro padronizado.
     */
    @Override
    public GasStation findByIdOrThrow(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Posto de Combustível não encontrado."));
    }

    /**
     * Busca posto ativo e garante que ele pertence ao usuário autenticado.
     */
    @Override
    public GasStation findMyGasStationById(UUID id) {
        GasStation station = findByIdOrThrow(id);
        gasStationDomainValidator.validateOwner(station, SecurityContextUtils.getCurrentUser());
        return station;
    }

    /**
     * Atualiza dados cadastrais simples do posto.
     */
    @Override
    @Transactional
    public GasStation updateGasStation(UUID id, GasStationDTO dto) {
        GasStation station = findMyGasStationById(id);
        applyFields(station, dto);

        station.setUpdatedAt(DateUtils.getEpochNow());
        GasStation updatedStation = repository.save(station);
        log.info("Gas station updated: stationId={}", updatedStation.getId());
        return updatedStation;
    }

    /**
     * Remove logicamente o posto quando pertence ao usuário autenticado.
     */
    @Override
    @Transactional
    public void softDelete(UUID id) {
        GasStation station = findMyGasStationById(id);
        gasStationDomainValidator.validateCanDelete(station);

        station.setDeletedAt(DateUtils.getEpochNow());
        repository.save(station);
        log.info("Gas station deleted: stationId={}", id);
    }

    /**
     * Atualiza somente campos enviados no contrato.
     */
    private void applyFields(GasStation station, GasStationDTO dto) {
        if (dto.getName() != null) {
            station.setName(dto.getName());
        }
        if (dto.getAddress() != null) {
            station.setAddress(dto.getAddress());
        }
        if (dto.getCity() != null) {
            station.setCity(dto.getCity());
        }
        if (dto.getState() != null) {
            station.setState(dto.getState());
        }
    }
}

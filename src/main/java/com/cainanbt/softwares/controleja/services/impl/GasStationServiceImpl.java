package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.GasStationRepository;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GasStationServiceImpl implements GasStationService {
    private final GasStationRepository repository;

    @Override
    public GasStation createGasStation(GasStationDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        GasStation station = GasStation.builder()
                .id(ID.generate())
                .name(dto.getName())
                .address(dto.getAddress())
                .city(dto.getCity())
                .state(dto.getState())
                .user(user)
                .createdAt(DateUtils.getEpochNow())
                .build();

        return repository.save(station);
    }

    @Override
    public List<GasStation> listMyGasStations() {
        return repository.findByUserIdAndDeletedAtIsNull(SecurityContextUtils.getCurrentUser().getId());
    }

    @Override
    public GasStation findByIdOrThrow(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Posto de Combustível não encontrado."));
    }

    @Override
    public GasStation updateGasStation(UUID id, GasStationDTO dto) {
        GasStation station = findByIdOrThrow(id);
        Users user = SecurityContextUtils.getCurrentUser();

        if (!station.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Este posto não pertence a você.");
        }

        if (dto.getName() != null) station.setName(dto.getName());
        if (dto.getAddress() != null) station.setAddress(dto.getAddress());
        if (dto.getCity() != null) station.setCity(dto.getCity());
        if (dto.getState() != null) station.setState(dto.getState());

        station.setUpdatedAt(DateUtils.getEpochNow());
        return repository.save(station);
    }

    @Override
    public void softDelete(UUID id) {
        GasStation station = findByIdOrThrow(id);
        Users user = SecurityContextUtils.getCurrentUser();

        if (!station.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Este posto não pertence a você.");
        }

        station.setDeletedAt(DateUtils.getEpochNow());
        repository.save(station);
    }
}

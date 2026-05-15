package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;


@Data
public class VehicleLogDTO {
    @NotNull(message = "O veículo é obrigatório")
    private UUID vehicleId;

    @NotNull(message = "A data é obrigatória")
    private Long date;

    @NotNull(message = "O odômetro é obrigatório")
    private BigDecimal odometerReading;

    private Double dashboardKml;
}

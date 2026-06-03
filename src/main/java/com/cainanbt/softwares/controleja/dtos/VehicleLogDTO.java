package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.utils.StrictBigDecimalDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
    @PositiveOrZero(message = "O odômetro não pode ser negativo")
    @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
    private BigDecimal odometerReading;

    @Positive(message = "A média do painel deve ser maior que zero")
    private Double dashboardKml;

    private DrivingPredominance drivingPredominance;
}

package com.cainanbt.softwares.controleja.dtos.responses;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Informa os limites cronológicos de odômetro para uma data do veículo.
 */
@Data
@Builder
public class VehicleOdometerContextDTO {
    private BigDecimal previousOdometer;
    private Long previousDate;
    private String previousSource;
    private BigDecimal nextOdometer;
    private Long nextDate;
    private String nextSource;
    private BigDecimal currentOdometer;
    private Long latestReadingDate;
    private Boolean retroactive;
}

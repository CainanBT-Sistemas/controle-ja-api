package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.utils.StrictBigDecimalDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleDTO {
    @NotBlank(message = "Apelido do veículo é obrigatório")
    @Size(max = 80, message = "O apelido do veículo deve ter no máximo 80 caracteres")
    private String name;

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 60, message = "A marca deve ter no máximo 60 caracteres")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 80, message = "O modelo deve ter no máximo 80 caracteres")
    private String model;

    @NotNull(message = "Ano é obrigatório")
    @Min(value = 1900, message = "Ano inválido")
    private Integer year;

    private String plate;

    @NotNull(message = "Quilometragem inicial é obrigatória")
    @PositiveOrZero(message = "A quilometragem inicial não pode ser negativa")
    @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
    private BigDecimal currentOdometer;

    @Positive(message = "A capacidade do tanque deve ser maior que zero")
    private Double tankCapacity;
}

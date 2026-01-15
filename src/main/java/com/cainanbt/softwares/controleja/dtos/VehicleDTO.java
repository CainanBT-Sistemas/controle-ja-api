package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleDTO {
    @NotBlank(message = "Apelido do veículo é obrigatório")
    private String name;

    @NotBlank(message = "Marca é obrigatória")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    private String model;

    @NotNull(message = "Ano é obrigatório")
    @Min(value = 1900, message = "Ano inválido")
    private Integer year;

    private String plate;

    @NotNull(message = "Quilometragem inicial é obrigatória")
    private BigDecimal currentOdometer;
}
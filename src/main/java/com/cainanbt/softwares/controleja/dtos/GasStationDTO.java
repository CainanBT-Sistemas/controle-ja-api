package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GasStationDTO {
    @NotBlank(message = "O nome do posto é obrigatório")
    private String name;

    private String address;
    private String city;
    private String state;
}

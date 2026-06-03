package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GasStationDTO {
    @NotBlank(message = "O nome do posto é obrigatório")
    @Size(max = 120, message = "O nome do posto deve ter no máximo 120 caracteres")
    private String name;

    @Size(max = 160, message = "O endereço deve ter no máximo 160 caracteres")
    private String address;
    @Size(max = 80, message = "A cidade deve ter no máximo 80 caracteres")
    private String city;
    @Size(max = 2, message = "O estado deve usar a sigla com 2 caracteres")
    private String state;
}

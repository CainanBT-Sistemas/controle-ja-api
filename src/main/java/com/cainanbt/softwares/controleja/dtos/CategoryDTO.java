package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryDTO {
    @NotBlank(message = "O nome da categoria é obrigatório")
    private String name;
    @NotBlank(message = "O tipo é obrigatório (DESPESA, RECEITA)")
    private String categoryType;
    private String icon;
    private String color;
    private UUID parentId;
}

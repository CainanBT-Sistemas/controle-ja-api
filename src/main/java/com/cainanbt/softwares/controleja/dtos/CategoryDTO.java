package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryDTO {
    @NotBlank(message = "O nome da categoria é obrigatório")
    @Size(max = 80, message = "O nome da categoria deve ter no máximo 80 caracteres")
    private String name;
    @NotBlank(message = "O tipo é obrigatório (DESPESA, RECEITA)")
    @Size(max = 40, message = "O tipo da categoria deve ter no máximo 40 caracteres")
    private String categoryType;
    private String icon;
    private String color;
    private UUID parentId;
}

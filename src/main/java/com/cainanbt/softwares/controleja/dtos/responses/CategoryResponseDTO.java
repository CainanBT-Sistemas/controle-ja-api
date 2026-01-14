package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Category;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryResponseDTO {
    private UUID id;
    private String name;
    private String categoryType;
    private Boolean isSubCategory;
    private String parentName; // Facilitador para o Frontend

    public static CategoryResponseDTO toDTO(Category entity) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCategoryType(entity.getCategoryType());
        dto.setIsSubCategory(entity.getIsSubCategory());

        if (entity.getSubCategory() != null) {
            dto.setParentName(entity.getSubCategory().getName());
        }

        return dto;
    }
}
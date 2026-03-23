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
    private String parentName;
    private String icon;
    private String color;
    private boolean isDefault;

    public static CategoryResponseDTO toDTO(Category entity) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCategoryType(entity.getCategoryType());
        dto.setIsSubCategory(entity.getIsSubCategory());
        dto.setIcon(entity.getIcon());
        dto.setColor(entity.getColor());
        dto.setDefault(entity.isDefault());

        if (entity.getSubCategory() != null) {
            dto.setParentName(entity.getSubCategory().getName());
        }

        return dto;
    }
}
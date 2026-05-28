package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Category;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryResponseDTO {
    private UUID id;
    private String name;
    private String categoryType;
    private boolean isSubCategory;
    private String parentName;
    private String icon;
    private String color;
    private Boolean isDefault;
    private UUID parentId;

    public static CategoryResponseDTO toDTO(Category entity) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCategoryType(entity.getCategoryType());
        dto.setSubCategory(entity.getIsSubCategory());
        dto.setIcon(entity.getIcon());
        dto.setColor(entity.getColor());
        dto.setIsDefault(entity.getIsDefault());

        if (entity.getSubCategory() != null) {
            dto.setParentName(entity.getSubCategory().getName());
            dto.setParentId(entity.getSubCategory().getId());
        }

        return dto;
    }
}
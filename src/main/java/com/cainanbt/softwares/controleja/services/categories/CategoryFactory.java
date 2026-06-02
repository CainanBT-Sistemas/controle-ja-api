package com.cainanbt.softwares.controleja.services.categories;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.utils.ID;

/**
 * Monta entidades de categoria mantendo construção separada da persistência.
 */
public class CategoryFactory {

    /**
     * Cria uma categoria ou subcategoria a partir do contrato recebido pela API.
     */
    public Category create(CategoryDTO dto, Users user, Category parent, long now) {
        return Category.builder()
                .id(ID.generate())
                .name(dto.getName())
                .categoryType(dto.getCategoryType())
                .icon(dto.getIcon())
                .color(dto.getColor())
                .enabled(true)
                .isSubCategory(parent != null)
                .isDefault(false)
                .subCategory(parent)
                .user(user)
                .createdAt(now)
                .build();
    }
}

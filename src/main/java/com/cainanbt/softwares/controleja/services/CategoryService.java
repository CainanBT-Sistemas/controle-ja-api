package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryService {
    Category createCategory(CategoryDTO dto);

    List<Category> listMyCategories();

    Optional<Category> findById(UUID categoryId);
    
    Category findByIdOrThrow(UUID id);
    
    Category updateCategory(UUID id, CategoryDTO dto);
    
    void softDelete(UUID id);

    void save(Category category);
}

package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;

import java.util.List;

public interface CategoryService {
    Category createCategory(CategoryDTO dto);

    List<Category> listMyCategories();
}

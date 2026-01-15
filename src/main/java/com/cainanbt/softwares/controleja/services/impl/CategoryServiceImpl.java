package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.repository = categoryRepository;
    }

    @Override
    public Category createCategory(CategoryDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        Category parent = null;
        if (dto.getParentId() != null) {
            parent = repository.findById(dto.getParentId())
                    .orElseThrow(() -> new BadRequestException("Erro", "Categoria pai não encontrada"));

            if (!parent.getUser().getId().equals(user.getId())) {
                throw new BadRequestException("Erro", "Categoria pai inválida");
            }
        }

        Category category = Category.builder()
                .id(ID.generate())
                .name(dto.getName())
                .categoryType(dto.getCategoryType())
                .enabled(true)
                .isSubCategory(parent != null)
                .subCategory(parent)
                .user(user)
                .createdAt(System.currentTimeMillis())
                .build();

        return repository.save(category);
    }

    @Override
    public List<Category> listMyCategories() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdAndDeletedAtIsNull(user.getId());
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id);
    }
}
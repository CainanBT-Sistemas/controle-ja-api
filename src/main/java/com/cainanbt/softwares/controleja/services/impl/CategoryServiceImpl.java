package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;


    @Override
    public Category createCategory(CategoryDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        Category parent = null;
        if (dto.getParentId() != null) {
            parent = repository.findByIdAndNotDeleted(dto.getParentId())
                    .orElseThrow(() -> new BadRequestException("Erro", "Categoria pai não encontrada"));

            if (!parent.getUser().getId().equals(user.getId())) {
                throw new BadRequestException("Erro", "Categoria pai inválida");
            }
        }

        Category category = Category.builder()
                .id(ID.generate())
                .name(dto.getName())
                .categoryType(dto.getCategoryType())
                .icon(dto.getIcon())
                .color(dto.getColor())
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
        return repository.findByIdAndNotDeleted(id);
    }
    
    @Override
    public Category findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.CATEGORY_NOT_FOUND,
                    "Categoria com ID " + id + " não encontrada ou já foi excluída"));
    }
    
    @Override
    public Category updateCategory(UUID id, CategoryDTO dto) {
        Category category = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        
        // Verify ownership
        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para alterar esta categoria");
        }
        
        // Update fields
        if (dto.getName() != null) {
            category.setName(dto.getName());
        }
        if (dto.getCategoryType() != null) {
            category.setCategoryType(dto.getCategoryType());
        }
        if (dto.getIcon() != null) {
            category.setIcon(dto.getIcon());
        }
        if (dto.getColor() != null) {
            category.setColor(dto.getColor());
        }
        
        // Update parent category if provided
        if (dto.getParentId() != null) {
            Category parent = repository.findByIdAndNotDeleted(dto.getParentId())
                    .orElseThrow(() -> new BadRequestException("Erro", "Categoria pai não encontrada"));
            
            if (!parent.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException("Erro", "Categoria pai inválida");
            }
            
            category.setSubCategory(parent);
            category.setIsSubCategory(true);
        }
        
        category.setUpdatedAt(System.currentTimeMillis());
        
        return repository.save(category);
    }
    
    @Override
    public void softDelete(UUID id) {
        Category category = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso negado", "Você não tem permissão para excluir esta categoria");
        }

        if (category.getDeletedAt() != null) {
            throw new BadRequestException("Erro", ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        category.setDeletedAt(System.currentTimeMillis());
        repository.save(category);
    }

    @Override
    public void save(Category category) {
        repository.save(category);
    }
}
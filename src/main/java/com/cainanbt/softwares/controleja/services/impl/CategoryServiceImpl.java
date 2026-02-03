package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.exceptions.models.InternalServerException;
import com.cainanbt.softwares.controleja.exceptions.models.NotFoundException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Category createCategory(CategoryDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        Category parent = null;
        if (dto.getParentId() != null) {
            parent = repository.findById(dto.getParentId())
                    .orElseThrow(() -> new NotFoundException("Categoria pai não encontrada", "A categoria pai especificada não existe."));

            if (!parent.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException("Acesso negado", "A categoria pai não pertence ao usuário.");
            }
        }

        try {
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
        } catch (Exception e) {
            throw new InternalServerException("Erro ao criar categoria", "Não foi possível criar a categoria. Tente novamente.", e);
        }
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

    @Override
    @Transactional
    public void save(Category category) {
        try {
            repository.save(category);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao salvar categoria", "Não foi possível salvar a categoria. Tente novamente.", e);
        }
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        Users user = SecurityContextUtils.getCurrentUser();
        
        Category category = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada", "A categoria especificada não existe."));
        
        if (!category.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Acesso negado", "Você não tem permissão para deletar esta categoria.");
        }
        
        try {
            category.setDeletedAt(System.currentTimeMillis());
            repository.save(category);
        } catch (Exception e) {
            throw new InternalServerException("Erro ao deletar categoria", "Não foi possível deletar a categoria. Tente novamente.", e);
        }
    }
}
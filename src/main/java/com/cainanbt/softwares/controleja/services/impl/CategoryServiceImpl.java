package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
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
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.PARENT_CATEGORY_NOT_FOUND));

            if (!parent.getUser().getId().equals(user.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_CATEGORY);
            }
            if (parent.getIsSubCategory()) {
                throw new BadRequestException("Ação não permitida", "Não é possível criar uma subcategoria dentro de outra. O limite é de 1 nível.");
            }
            long totalFilhos = repository.countActiveSubCategories(parent.getId());
            if (totalFilhos >= 2) {
                throw new BadRequestException("Limite Atingido", "Você atingiu o limite de 2 subcategorias por categoria pai. Assine o Premium para liberar acesso ilimitado!");
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
                .isDefault(false)
                .subCategory(parent)
                .user(user)
                .createdAt(DateUtils.getEpochNow())
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
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));
    }

    @Override
    public Category updateCategory(UUID id, CategoryDTO dto) {
        Category category = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CATEGORY);
        }

        if (dto.getName() != null) category.setName(dto.getName());
        if (dto.getCategoryType() != null) category.setCategoryType(dto.getCategoryType());
        if (dto.getIcon() != null) category.setIcon(dto.getIcon());
        if (dto.getColor() != null) category.setColor(dto.getColor());

        if (dto.getParentId() != null) {
            Category parent = repository.findByIdAndNotDeleted(dto.getParentId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.PARENT_CATEGORY_NOT_FOUND));

            if (!parent.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_CATEGORY);
            }

            if (parent.getIsSubCategory()) {
                throw new BadRequestException("Ação não permitida", "A categoria destino já é uma subcategoria.");
            }

            category.setSubCategory(parent);
            category.setIsSubCategory(true);
        }

        category.setUpdatedAt(DateUtils.getEpochNow());

        return repository.save(category);
    }

    @Override
    public void softDelete(UUID id) {
        Category category = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CATEGORY);
        }

        if (category.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }
        category.setDeletedAt(DateUtils.getEpochNow());
        repository.save(category);
    }

    @Override
    public void save(Category category) {
        repository.save(category);
    }
}
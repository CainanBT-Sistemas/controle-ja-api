package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.categories.CategoryDomainValidator;
import com.cainanbt.softwares.controleja.services.categories.CategoryFactory;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryDomainValidator categoryDomainValidator = new CategoryDomainValidator();
    private final CategoryFactory categoryFactory = new CategoryFactory();

    private final CategoryRepository repository;
    private final TransactionRepository transactionRepository;

    /**
     * Cria categoria ou subcategoria respeitando propriedade, limite e profundidade máxima.
     */
    @Override
    @Transactional
    public Category createCategory(CategoryDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();
        Category parent = resolveParentForCreate(dto, user);

        return repository.save(categoryFactory.create(dto, user, parent, DateUtils.getEpochNow()));
    }

    /**
     * Lista somente categorias ativas do usuário autenticado.
     */
    @Override
    public List<Category> listMyCategories() {
        Users user = SecurityContextUtils.getCurrentUser();
        return repository.findByUserIdAndDeletedAtIsNull(user.getId());
    }

    /**
     * Busca categoria ativa por id sem validar usuário, usado por fluxos que fazem essa regra fora daqui.
     */
    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findByIdAndNotDeleted(id);
    }

    /**
     * Busca categoria ativa por id ou lança erro padronizado.
     */
    @Override
    public Category findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));
    }

    /**
     * Busca categoria ativa e garante que ela pertence ao usuário autenticado.
     */
    @Override
    public Category findMyCategoryById(UUID id) {
        Category category = findByIdOrThrow(id);
        categoryDomainValidator.validateOwner(category, SecurityContextUtils.getCurrentUser());
        return category;
    }

    /**
     * Atualiza dados simples da categoria e permite mover para outro pai válido.
     */
    @Override
    @Transactional
    public Category updateCategory(UUID id, CategoryDTO dto) {
        Category category = findMyCategoryById(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();

        applyCategoryFields(category, dto);
        applyParentChange(category, dto, currentUser);

        category.setUpdatedAt(DateUtils.getEpochNow());

        return repository.save(category);
    }

    /**
     * Remove logicamente a categoria e suas subcategorias quando permitido.
     */
    @Override
    @Transactional
    public void softDelete(UUID id) {
        Category category = findMyCategoryById(id);
        categoryDomainValidator.validateCanDelete(category);
        List<Category> subCategories = repository.findBySubCategoryIdAndDeletedAtIsNull(category.getId());
        validateNoDependentTransactions(category, subCategories);

        long now = DateUtils.getEpochNow();
        softDeleteSubCategories(subCategories, now);

        category.setDeletedAt(now);
        repository.save(category);
    }

    /**
     * Persiste categoria montada por fluxos internos sem aplicar regras de usuário autenticado.
     */
    @Override
    public Category save(Category category) {
        return repository.save(category);
    }

    /**
     * Busca categoria do usuário por nome em fluxos internos críticos.
     */
    @Override
    public Category findCategoryByUserAndName(Users user, String categoryName) {
        return repository.findByUserIdAndNameAndDeletedAtIsNull(user.getId(), categoryName)
                .orElseThrow(() -> new BadRequestException(
                        ConstsMessages.CRITICAL_ERROR_TITLE,
                        ConstsMessages.CATEGORY_NOT_FOUND + " (" + categoryName + "). " + ConstsMessages.SYSTEM_CRITICAL_ERROR
                ));
    }

    @Override
    public Category findTransferCategory(Users user) {
        List<Category> categories =
                repository.findAllByUserIdAndCategoryTypeAndIsDefaultTrueAndEnabledTrueAndDeletedAtIsNull(
                        user.getId(),
                        TransactionType.TRANSFERENCIA.name()
                );
        if (categories.size() != 1) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "A categoria tecnica de transferencia nao esta configurada corretamente para este usuario."
            );
        }
        return categories.get(0);
    }

    @Override
    @Transactional
    public Category ensureVehicleEntryCategory(Users user, boolean refuel) {
        Category vehicleParent = findOrCreateDefaultCategory(
                user,
                "Veículo",
                "directions_car",
                "#3F51B5",
                null
        );
        return findOrCreateDefaultCategory(
                user,
                refuel ? "Abastecimento" : "Manutenção",
                refuel ? "local_gas_station" : "build",
                "#3F51B5",
                vehicleParent
        );
    }

    private Category findOrCreateDefaultCategory(
            Users user,
            String name,
            String icon,
            String color,
            Category parentCategory
    ) {
        return repository.findAllByUserIdAndNameAndDeletedAtIsNull(user.getId(), name)
                .stream()
                .filter(category -> parentCategory == null
                        ? category.getSubCategory() == null
                        : category.getSubCategory() != null
                        && category.getSubCategory().getId().equals(parentCategory.getId()))
                .findFirst()
                .orElseGet(() -> repository.save(Category.builder()
                        .id(ID.generate())
                        .name(name)
                        .categoryType(TransactionType.DESPESA.name())
                        .enabled(true)
                        .isDefault(true)
                        .isSubCategory(parentCategory != null)
                        .icon(icon)
                        .color(color)
                        .user(user)
                        .createdAt(DateUtils.getEpochNow())
                        .subCategory(parentCategory)
                        .build()));
    }

    /**
     * Resolve e valida o pai informado na criação de subcategoria.
     */
    private Category resolveParentForCreate(CategoryDTO dto, Users user) {
        if (dto.getParentId() == null) {
            return null;
        }

        Category parent = findParentOrThrow(dto.getParentId());
        categoryDomainValidator.validateParentCategory(parent, user);
        categoryDomainValidator.validateCanAddChildCategory(repository.countActiveSubCategories(parent.getId()));
        return parent;
    }

    /**
     * Busca a categoria pai ativa ou lança erro claro para o cliente.
     */
    private Category findParentOrThrow(UUID parentId) {
        return repository.findByIdAndNotDeleted(parentId)
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.PARENT_CATEGORY_NOT_FOUND));
    }

    /**
     * Atualiza os campos simples da categoria quando enviados.
     */
    private void applyCategoryFields(Category category, CategoryDTO dto) {
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
    }

    /**
     * Move a categoria para outro pai válido quando parentId é informado.
     */
    private void applyParentChange(Category category, CategoryDTO dto, Users currentUser) {
        if (dto.getParentId() == null) {
            return;
        }

        Category parent = findParentOrThrow(dto.getParentId());
        if (category.getId().equals(parent.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A categoria não pode ser pai dela mesma.");
        }
        categoryDomainValidator.validateParentCategory(parent, currentUser);

        category.setSubCategory(parent);
        category.setIsSubCategory(true);
    }

    /**
     * Bloqueia remoção quando a categoria ou suas filhas possuem transações vinculadas.
     */
    private void validateNoDependentTransactions(Category category, List<Category> subCategories) {
        long dependentTransactions = transactionRepository.countByCategoryId(category.getId());
        for (Category subCategory : subCategories) {
            dependentTransactions += transactionRepository.countByCategoryId(subCategory.getId());
        }
        if (dependentTransactions > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível excluir categoria com transações vinculadas.");
        }
    }

    /**
     * Marca subcategorias como removidas usando a mesma data da categoria pai.
     */
    private void softDeleteSubCategories(List<Category> subCategories, long now) {
        if (subCategories.isEmpty()) {
            return;
        }
        for (Category subCategory : subCategories) {
            subCategory.setDeletedAt(now);
        }
        repository.saveAll(subCategories);
    }
}

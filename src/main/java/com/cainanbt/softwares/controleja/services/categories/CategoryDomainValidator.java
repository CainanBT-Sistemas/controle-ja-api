package com.cainanbt.softwares.controleja.services.categories;

import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;

/**
 * Centraliza regras de propriedade, hierarquia e exclusão das categorias.
 */
public class CategoryDomainValidator {

    private static final int FREE_PLAN_CHILD_CATEGORY_LIMIT = 2;

    /**
     * Garante que a categoria pertence ao usuário autenticado antes de expor ou alterar dados.
     */
    public void validateOwner(Category category, Users currentUser) {
        if (category == null || category.getUser() == null || currentUser == null
                || !category.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_CATEGORY);
        }
    }

    /**
     * Garante que a categoria pai pertence ao usuário e não é uma subcategoria.
     */
    public void validateParentCategory(Category parent, Users currentUser) {
        validateOwner(parent, currentUser);
        if (Boolean.TRUE.equals(parent.getIsSubCategory())) {
            throw new BadRequestException("Ação não permitida", "Não é possível criar uma subcategoria dentro de outra. O limite é de 1 nível.");
        }
    }

    /**
     * Bloqueia criação acima do limite permitido de subcategorias no plano atual.
     */
    public void validateCanAddChildCategory(long activeChildrenCount) {
        if (activeChildrenCount >= FREE_PLAN_CHILD_CATEGORY_LIMIT) {
            throw new BadRequestException(
                    "Limite Atingido",
                    "Você atingiu o limite de 2 subcategorias por categoria pai. Assine o Premium para liberar acesso ilimitado!"
            );
        }
    }

    /**
     * Impede exclusão de categoria padrão, já removida ou protegida por regra do sistema.
     */
    public void validateCanDelete(Category category) {
        if (Boolean.TRUE.equals(category.getIsDefault())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Categorias de sistema não podem ser excluídas.");
        }
        if (category.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }
    }
}

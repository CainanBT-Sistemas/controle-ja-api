package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryService {
    /**
     * Cria categoria ou subcategoria para o usuário autenticado.
     */
    Category createCategory(CategoryDTO dto);

    /**
     * Lista as categorias ativas do usuário autenticado.
     */
    List<Category> listMyCategories();

    /**
     * Busca categoria ativa por id sem aplicar regra de propriedade.
     */
    Optional<Category> findById(UUID categoryId);

    /**
     * Busca categoria ativa por id ou lança erro de entidade não encontrada.
     */
    Category findByIdOrThrow(UUID id);

    /**
     * Busca categoria ativa garantindo que pertence ao usuário autenticado.
     */
    Category findMyCategoryById(UUID id);

    /**
     * Atualiza categoria do usuário autenticado.
     */
    Category updateCategory(UUID id, CategoryDTO dto);

    /**
     * Remove logicamente categoria e suas subcategorias.
     */
    void softDelete(UUID id);

    /**
     * Persiste categoria criada por fluxos internos, como categorias padrão do usuário.
     */
    Category save(Category category);

    /**
     * Localiza categoria ativa por usuário e nome para fluxos internos.
     */
    Category findCategoryByUserAndName(Users user, String categoryName);
}

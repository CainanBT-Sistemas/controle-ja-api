package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    /**
     * Lista categorias ativas pertencentes ao usuário.
     */
    List<Category> findByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Busca categoria ativa por id ignorando registros removidos logicamente.
     */
    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Category> findByIdAndNotDeleted(UUID id);

    /**
     * Conta subcategorias ativas de uma categoria pai.
     */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.subCategory.id = :parentId AND c.deletedAt IS NULL")
    long countActiveSubCategories(@Param("parentId") UUID parentId);

    /**
     * Localiza categoria ativa por usuário e nome.
     */
    Optional<Category> findByUserIdAndNameAndDeletedAtIsNull(UUID userId, String name);

    /**
     * Lista subcategorias ativas de uma categoria pai.
     */
    List<Category> findBySubCategoryIdAndDeletedAtIsNull(UUID parentId);
}

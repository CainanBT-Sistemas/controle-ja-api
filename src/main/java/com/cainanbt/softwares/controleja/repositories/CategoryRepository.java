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
    List<Category> findByUserIdAndDeletedAtIsNull(UUID userId);
    
    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Category> findByIdAndNotDeleted(UUID id);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.subCategory.id = :parentId AND c.deletedAt IS NULL")
    long countActiveSubCategories(@Param("parentId") UUID parentId);

    Optional<Category> findByUserIdAndNameAndDeletedAtIsNull(UUID userId, String name);

    List<Category> findBySubCategoryIdAndDeletedAtIsNull(UUID parentId);
}
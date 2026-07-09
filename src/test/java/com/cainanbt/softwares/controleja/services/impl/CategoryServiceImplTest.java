package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository repository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CategoryServiceImpl service;

    @Test
    void findTransferCategoryUsesOnlyTechnicalIdentity() {
        UUID userId = UUID.randomUUID();
        Users user = Users.builder().id(userId).build();
        Category technicalCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Transferencia")
                .categoryType(TransactionType.TRANSFERENCIA.name())
                .isDefault(true)
                .enabled(true)
                .user(user)
                .build();

        when(repository.findAllByUserIdAndCategoryTypeAndIsDefaultTrueAndEnabledTrueAndDeletedAtIsNull(
                userId,
                TransactionType.TRANSFERENCIA.name()
        )).thenReturn(List.of(technicalCategory));

        assertSame(technicalCategory, service.findTransferCategory(user));
        verify(repository, never()).findByUserIdAndNameAndDeletedAtIsNull(userId, "Outros");
    }

    @Test
    void findTransferCategoryReturnsControlledErrorWhenMissing() {
        UUID userId = UUID.randomUUID();
        Users user = Users.builder().id(userId).build();
        when(repository.findAllByUserIdAndCategoryTypeAndIsDefaultTrueAndEnabledTrueAndDeletedAtIsNull(
                userId,
                TransactionType.TRANSFERENCIA.name()
        )).thenReturn(List.of());

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.findTransferCategory(user)
        );

        assertEquals(
                "A categoria tecnica de transferencia nao esta configurada corretamente para este usuario.",
                error.getDetail()
        );
    }

    @Test
    void findTransferCategoryRejectsDuplicateTechnicalCategories() {
        UUID userId = UUID.randomUUID();
        Users user = Users.builder().id(userId).build();
        Category first = Category.builder().id(UUID.randomUUID()).user(user).build();
        Category second = Category.builder().id(UUID.randomUUID()).user(user).build();
        when(repository.findAllByUserIdAndCategoryTypeAndIsDefaultTrueAndEnabledTrueAndDeletedAtIsNull(
                userId,
                TransactionType.TRANSFERENCIA.name()
        )).thenReturn(List.of(first, second));

        assertThrows(BadRequestException.class, () -> service.findTransferCategory(user));
        verify(repository, never()).findByUserIdAndNameAndDeletedAtIsNull(userId, "Transferencia");
    }
}

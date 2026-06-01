package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Cria categoria ou subcategoria para o usuário autenticado.
     */
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> create(@RequestBody @Valid CategoryDTO categoryDTO) {
        return ResponseEntity.ok(CategoryResponseDTO.toDTO(categoryService.createCategory(categoryDTO)));
    }

    /**
     * Lista as categorias ativas do usuário autenticado.
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> listAll() {
        return ResponseEntity.ok(categoryService.listMyCategories().stream().map(CategoryResponseDTO::toDTO).toList());
    }

    /**
     * Consulta categoria ativa garantindo propriedade do usuário autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(CategoryResponseDTO.toDTO(categoryService.findMyCategoryById(id)));
    }

    /**
     * Atualiza dados da categoria e permite mover para uma categoria pai válida.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid CategoryDTO categoryDTO) {
        return ResponseEntity.ok(CategoryResponseDTO.toDTO(categoryService.updateCategory(id, categoryDTO)));
    }

    /**
     * Remove logicamente categoria e subcategorias quando não há transações vinculadas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        categoryService.softDelete(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.DELETE_SUCCESS);
        return ResponseEntity.ok(response);
    }
}

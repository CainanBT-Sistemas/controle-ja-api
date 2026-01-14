package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.services.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("controle_ja_api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;


    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public ResponseEntity<CategoryResponseDTO> create(@RequestBody @Valid CategoryDTO categoryDTO) {
        return ResponseEntity.ok(CategoryResponseDTO.toDTO(categoryService.createCategory(categoryDTO)));
    }

    public ResponseEntity<List<CategoryResponseDTO>> listAll() {
        return ResponseEntity.ok(categoryService.listMyCategories().stream().map(CategoryResponseDTO::toDTO).toList());
    }
}

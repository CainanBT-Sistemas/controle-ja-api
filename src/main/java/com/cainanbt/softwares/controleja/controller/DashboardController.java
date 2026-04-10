package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardFullSummaryDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;
import com.cainanbt.softwares.controleja.services.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/expenses-category")
    public ResponseEntity<List<ChartDataDTO>> getExpensesByCategory(@RequestParam Long start, @RequestParam Long end) {
        return ResponseEntity.ok(service.getExpensesByCategory(start, end));
    }

    @GetMapping("/incomes-category")
    public ResponseEntity<List<ChartDataDTO>> getIncomesByCategory(@RequestParam Long start, @RequestParam Long end) {
        return ResponseEntity.ok(service.getIncomesByCategory(start, end));
    }

    @GetMapping("/fuel-comparison")
    public ResponseEntity<List<ChartDataDTO>> getFuelComparison(@RequestParam Long start, @RequestParam Long end) {
        return ResponseEntity.ok(service.getFuelComparison(start, end));
    }

    @GetMapping("/evolution")
    public ResponseEntity<List<ChartDataDTO>> getEvolution(
            @RequestParam Long start,
            @RequestParam Long end,
            @RequestParam(required = false) UUID categoryId) {
        return ResponseEntity.ok(service.getEvolution(start, end, categoryId));
    }

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryDTO> getSummary(@RequestParam Long start, @RequestParam Long end) {
        return ResponseEntity.ok(service.getSummary(start, end));
    }

    @GetMapping("/full-summary")
    public ResponseEntity<DashboardFullSummaryDTO> getFullSummary(@RequestParam Long start, @RequestParam Long end) {
        return ResponseEntity.ok(service.getFullSummary(start, end));
    }
}
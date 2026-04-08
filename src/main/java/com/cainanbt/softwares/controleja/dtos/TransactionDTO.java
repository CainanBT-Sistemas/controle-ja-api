package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionDTO {

    @NotBlank(message = "o Nome é obrigatóro")
    private String name;

    private String description;

    @NotNull(message = "O tipo é obrigatório (RECEITA, DESPESA)")
    private TransactionType type;

    @NotNull(message = "O valor é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "A data é obrigatória")
    private Long date;

    @NotNull(message = "A conta de origem é obrigatória")
    private UUID accountId;

    @NotNull(message = "A categoria é obrigatória")
    private UUID categoryId;

    @Min(value = 1, message = "O número mínimo de parcelas é 1")
    private Integer installments;

    @NotNull(message = "Deve ser informado se a transação foi paga ou não")
    private Boolean paid;

    //TRANSFERENCIA
    private UUID targetAccountId;

    //CARTÃO DE CREDITO OU FINANCIAMENTOS/PARCELAMENTOS
    private UUID creditCardId;
    private UUID targetInvoiceId;
    @NotNull(message = "Deve ser informado se a transação é fixa ou não")
    private Boolean isFixed;
    private RecurrenceFrequency recurrenceFrequency;
    private Long recurrenceEndDate;

    //VEICULOS
    private UUID vehicleId;
    private BigDecimal currentOdometer;
    private Double liters;
    private FuelType fuelType;
    private Double efficiency;

}

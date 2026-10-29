package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountRecoveryRequestDTO {
    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotNull(message = "A finalidade é obrigatória")
    private AccountActionPurpose purpose;
}

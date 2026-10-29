package com.cainanbt.softwares.controleja.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountRecoveryRequestResponseDTO {
    private final String message;
    private final String senderEmail;
}

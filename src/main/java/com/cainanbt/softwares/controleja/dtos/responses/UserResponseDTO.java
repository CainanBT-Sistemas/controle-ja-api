package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Users;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponseDTO {
    private String id;
    private String username;
    private String email;
    private long createdAt;
    private AuthResponseDTO tokens;
    private UserEntitlementsDTO entitlements;

    public static UserResponseDTO toDTO(Users newUser) {
        return new UserResponseDTO(
                newUser.getId().toString(),
                newUser.getUsername(),
                newUser.getEmail(),
                newUser.getCreatedAt(),
                null,
                null
        );
    }
}

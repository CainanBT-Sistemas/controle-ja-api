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

    public static UserResponseDTO toDTO(Users newUser) {
        return new UserResponseDTO(null, newUser.getUsername(), newUser.getEmail(), newUser.getCreatedAt(), null);
    }
}

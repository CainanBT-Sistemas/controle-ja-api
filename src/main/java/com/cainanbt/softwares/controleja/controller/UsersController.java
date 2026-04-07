package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UpdateProfileDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/users")
public class UsersController {

    private final UsersService usersService;

    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> createNewUser(@RequestBody @Valid InsertUpdateUserDTO insertUpdateUser, HttpServletRequest request) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.createNewUser(insertUpdateUser, request)));
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody @Valid PasswordChangeDTO passwordChangeDTO) {
        usersService.changePassword(passwordChangeDTO);
        Map<String, String> response = new HashMap<>();
        response.put("message", ConstsMessages.PASSWORD_CHANGED_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.updateProfile(updateProfileDTO)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        UUID userId = UUID.fromString(id);
        boolean result = usersService.deleteUser(userId);
        Map<String, String> response = new HashMap<>();
        if (result) {
            response.put("message", "Usuário excluído com sucesso");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Usuário não foi excluido");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("reset/{id}")
    public ResponseEntity<?> resetUser(@PathVariable String id) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.resetUser(UUID.fromString(id))));
    }
}
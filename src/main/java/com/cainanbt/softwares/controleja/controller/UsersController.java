package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserResponseDTO;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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

}

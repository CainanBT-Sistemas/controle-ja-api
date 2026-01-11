package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserResponseDTO;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("controle_ja_api/v1/users")
public class UsersController {

    private final UsersService usersService;

    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> createNewUser(@RequestBody InsertUpdateUserDTO insertUpdateUser, HttpServletRequest request){
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.createNewUser(insertUpdateUser, request)));
    }

    @GetMapping()
    public ResponseEntity<?> teste(){
        SecurityContextUtils.getUserLogged();
        return ResponseEntity.ok("OK");
    }

}

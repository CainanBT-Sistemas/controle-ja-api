package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.UUID;

public interface UsersService extends UserDetailsService {

    public Optional<Users> getUserByEmailAndId(String email, UUID id);

    public Optional<Users> getUserByEmail(String email);

    public Users updateTokens(UserUpdateTokenDTO adapter);

    public Users createNewUser(InsertUpdateUserDTO user,HttpServletRequest request);

}

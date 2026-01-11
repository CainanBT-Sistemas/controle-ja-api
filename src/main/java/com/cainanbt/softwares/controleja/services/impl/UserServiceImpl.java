package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.enums.RoleEnum;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl implements UsersService {

    private final UsersRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UsersRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<Users> getUserByEmailAndId(String email, UUID id) {
        return userRepository.findByEmailIgnoreCaseAndId(email, id);
    }

    @Override
    public Optional<Users> getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Users updateTokens(UserUpdateTokenDTO adapter) {
        return userRepository.findById(adapter.getId()).map(user -> {
            user.setRefreshToken(adapter.getRefreshToken());
            user.setRefreshTokenExpiry(adapter.getRefreshExpiration());
            return userRepository.save(user);
        }).orElseThrow(() -> new BadRequestException("OOPS", "Parece que houve um erro critico no sistema informe o desenvolvedor"));
    }

    @Override
    public Users createNewUser(InsertUpdateUserDTO user,HttpServletRequest request) {
        try{
            if(isValidUser(user)) {
                Users save = userRepository.save(Users.builder()
                    .id(ID.generate())
                    .username(user.getUsername())
                    .password(passwordEncoder.encode(user.getPassword()))
                    .email(user.getEmail())
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .role(RoleEnum.ROLE_USER.getValue())
                    .oauth2User(false)
                    .createdAt(DateUtils.localDateTimeToEpoch(LocalDateTime.now()))
                    .build());

            }
            return null;
        }catch (Exception e){
            throw new BadRequestException("Falha critica no sistema: ", e.getMessage());
        }

    }

    private boolean isValidUser(InsertUpdateUserDTO user) {
        if (Objects.isNull(user.getEmail())) {
            throw new BadRequestException("Erro ao criar usuário", "Campo email é um campo obrigatório");
        }
        if (Objects.isNull(user.getUsername())) {
            throw new BadRequestException("Erro ao criar usuário", "Campo email é um campo username");
        }
        if (Objects.isNull(user.getPassword())) {
            throw new BadRequestException("Erro ao criar usuário", "Campo email é um campo password");
        }
        if (userRepository.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new BadRequestException("OPS", "Não foi possível cadastrar usuário, este email já está em uso.");
        }
        if(!Pattern.compile("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$").matcher(user.getEmail()).matches()){
            throw new BadRequestException("Erro ao criar usuário", "Email informado não é valido");
        }
        return true;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Users> usersOptional = userRepository.findByEmailIgnoreCase(email);
        if(usersOptional.isEmpty()){
            throw new BadRequestException("Falha de Login","Email ou senha incorretos. Tente novamente");
        }
        Users user = usersOptional.get();
        if(!user.getEnabled() || !user.getAccountNonLocked()){
            throw new BadRequestException("Acesso negado","Usuário bloqueado");
        }
        return new UserAuthenticateDTO(user);
    }
}

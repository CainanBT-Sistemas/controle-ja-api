package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UpdateProfileDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.RoleEnum;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.services.users.UserDefaultDataInitializer;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserServiceImpl implements UsersService {

    private final UsersRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDefaultDataInitializer defaultDataInitializer;
    private final EntityManager entityManager;

    /**
     * Busca usuario pelo email e id, usada para validacoes que exigem os dois identificadores.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Users> getUserByEmailAndId(String email, UUID id) {
        return userRepository.findByEmailIgnoreCaseAndId(email, id);
    }

    /**
     * Busca usuario por email ignorando caixa alta/baixa.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Users> getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Atualiza o refresh token persistido para permitir invalidacao por rotacao.
     */
    @Override
    @Transactional
    public Users updateTokens(UserUpdateTokenDTO adapter) {
        return userRepository.findById(adapter.getId()).map(user -> {
            user.setRefreshToken(adapter.getRefreshToken());
            user.setRefreshTokenExpiry(adapter.getRefreshExpiration());
            return userRepository.save(user);
        }).orElseThrow(() -> new BadRequestException(ConstsMessages.OOPS_TITLE, ConstsMessages.SYSTEM_CRITICAL_ERROR));
    }

    /**
     * Cria o usuario e inicializa os dados padrao em uma unica transacao.
     */
    @Override
    @Transactional
    public Users createNewUser(InsertUpdateUserDTO userDTO, HttpServletRequest request) {
        String normalizedEmail = userDTO.getEmail().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new BadRequestException(ConstsMessages.REGISTRATION_ERROR_TITLE, ConstsMessages.EMAIL_IN_USE);
        }
        try {
            Users newUser = Users.builder()
                    .id(ID.generate())
                    .username(userDTO.getUsername().trim())
                    .password(passwordEncoder.encode(userDTO.getPassword()))
                    .email(normalizedEmail)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .role(RoleEnum.ROLE_USER.getValue())
                    .oauth2User(false)
                    .createdAt(DateUtils.getEpochNow())
                    .build();

            Users saved = userRepository.save(newUser);
            defaultDataInitializer.initialize(saved);
            return saved;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar usuario e dados iniciais para email={}", normalizedEmail, e);
            throw new BadRequestException(ConstsMessages.CRITICAL_ERROR_TITLE, ConstsMessages.DATABASE_SAVE_ERROR);
        }
    }

    /**
     * Carrega o usuario autenticavel para o Spring Security.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Users> usersOptional = userRepository.findByEmailIgnoreCase(email);
        if (usersOptional.isEmpty()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.WRONG_LOGIN_CREDENTIALS);
        }
        Users user = usersOptional.get();
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.BLOCKED_USER);
        }
        return new UserAuthenticateDTO(user);
    }

    /**
     * Valida a senha atual e salva a nova senha criptografada.
     */
    @Override
    @Transactional
    public void changePassword(PasswordChangeDTO passwordChangeDTO) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        if (!passwordEncoder.matches(passwordChangeDTO.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_CURRENT_PASSWORD);
        }

        currentUser.setPassword(passwordEncoder.encode(passwordChangeDTO.getNewPassword()));
        currentUser.setUpdatedAt(DateUtils.getEpochNow());
        userRepository.save(currentUser);
    }

    /**
     * Atualiza o nome publico exibido para o usuario autenticado.
     */
    @Override
    @Transactional
    public Users updateProfile(UpdateProfileDTO dto) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        currentUser.setUsername(dto.getUsername().trim());
        currentUser.setUpdatedAt(DateUtils.getEpochNow());

        return userRepository.save(currentUser);
    }

    /**
     * Desativa a propria conta do usuario e invalida refresh token vigente.
     */
    @Override
    @Transactional
    public boolean deleteUser(UUID id) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        if (!currentUser.getId().equals(id)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCESS_DENIED);
        }
        Optional<Users> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            Users u = userOpt.get();
            u.setDeletedAt(DateUtils.getEpochNow());
            u.setEnabled(false);
            u.setAccountNonLocked(false);
            u.setRefreshToken(null);
            userRepository.save(u);
            return true;
        }
        return false;
    }

    /**
     * Remove dados operacionais do usuario e recria carteira/categorias padrao.
     */
    @Override
    @Transactional
    public Users resetUser(UUID uuid) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        if (!currentUser.getId().equals(uuid)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCESS_DENIED);
        }
        Optional<Users> userOpt = userRepository.findById(uuid);
        if (userOpt.isPresent()) {
            Users u = userOpt.get();
            UUID userId = u.getId();
            userRepository.deleteVehicleLogsByUserId(userId);
            userRepository.deleteInstallmentsByUserId(userId);
            userRepository.deleteInvoicesByUserId(userId);
            userRepository.deleteTransactionsByUserId(userId);
            userRepository.deleteCreditCardsByUserId(userId);
            userRepository.deleteVehiclesByUserId(userId);
            userRepository.deleteSubCategoriesByUserId(userId);
            userRepository.deleteCategoriesByUserId(userId);
            userRepository.deleteAccountsByUserId(userId);
            entityManager.flush();
            entityManager.clear();

            userOpt = userRepository.findById(uuid);
            if (userOpt.isPresent()) {
                u = userOpt.get();
                defaultDataInitializer.initialize(u);
                return u;
            }
        }
        throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.FAILURE_TO_FIND_USER);
    }
}

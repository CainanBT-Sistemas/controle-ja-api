package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UpdateProfileDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.UUID;

public interface UsersService extends UserDetailsService {

    /**
     * Busca um usuario ativo pelo par email e identificador.
     */
    Optional<Users> getUserByEmailAndId(String email, UUID id);

    /**
     * Busca usuario por email ignorando diferenca entre maiusculas e minusculas.
     */
    Optional<Users> getUserByEmail(String email);

    /**
     * Persiste o refresh token vigente do usuario autenticado.
     */
    Users updateTokens(UserUpdateTokenDTO adapter);

    /**
     * Invalida o refresh token em transacao independente.
     */
    void invalidateRefreshToken(UUID userId);

    /**
     * Cria a conta do usuario e os dados iniciais obrigatorios.
     */
    Users createNewUser(InsertUpdateUserDTO user, HttpServletRequest request);

    /**
     * Troca a senha do usuario autenticado depois de confirmar a senha atual.
     */
    void changePassword(PasswordChangeDTO passwordChangeDTO);

    /**
     * Atualiza informacoes publicas do perfil do usuario autenticado.
     */
    Users updateProfile(UpdateProfileDTO updateProfileDTO);

    /**
     * Desativa a propria conta do usuario autenticado.
     */
    boolean deleteUser(UUID id);

    /**
     * Remove dados operacionais e recria a estrutura inicial do usuario.
     */
    Users resetUser(UUID uuid);
}

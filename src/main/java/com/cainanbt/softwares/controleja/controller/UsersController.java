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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("controle_ja_api/v1/users")
public class UsersController {

    private final UsersService usersService;

    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    /**
     * Cria um novo usuario e inicializa a estrutura basica de conta e categorias.
     */
    @PostMapping("/register")
    public ResponseEntity<?> createNewUser(@RequestBody @Valid InsertUpdateUserDTO insertUpdateUser, HttpServletRequest request) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.createNewUser(insertUpdateUser, request)));
    }

    /**
     * Altera a senha do usuario autenticado depois de validar a senha atual.
     */
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody @Valid PasswordChangeDTO passwordChangeDTO) {
        usersService.changePassword(passwordChangeDTO);
        return ResponseEntity.ok(Map.of("message", ConstsMessages.PASSWORD_CHANGED_SUCCESS));
    }

    /**
     * Atualiza dados publicos do perfil do usuario autenticado.
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.updateProfile(updateProfileDTO)));
    }

    /**
     * Desativa a conta do proprio usuario autenticado sem apagar historico fisico.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        usersService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Usuário excluído com sucesso"));
    }

    /**
     * Reinicializa os dados operacionais do proprio usuario autenticado.
     */
    @PostMapping("/{id}/reset")
    public ResponseEntity<?> resetUserByPost(@PathVariable UUID id) {
        return ResponseEntity.ok(UserResponseDTO.toDTO(usersService.resetUser(id)));
    }

    /**
     * Endpoint legado mantido para compatibilidade; prefira POST /users/{id}/reset.
     */
    @GetMapping("reset/{id}")
    public ResponseEntity<?> resetUser(@PathVariable UUID id) {
        return resetUserByPost(id);
    }
}

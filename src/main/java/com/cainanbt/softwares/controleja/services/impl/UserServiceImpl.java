package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RoleEnum;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserServiceImpl implements UsersService {

    private final UsersRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final AccountsService accountsService;

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
    public Users createNewUser(InsertUpdateUserDTO userDTO, HttpServletRequest request) {
        if (userRepository.findByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new BadRequestException("Erro de Cadastro", "Este email já está em uso.");
        }
        try {
            Users newUser = Users.builder()
                    .id(ID.generate())
                    .username(userDTO.getUsername())
                    .password(passwordEncoder.encode(userDTO.getPassword()))
                    .email(userDTO.getEmail())
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .role(RoleEnum.ROLE_USER.getValue())
                    .oauth2User(false)
                    .createdAt(DateUtils.localDateTimeToEpoch(LocalDateTime.now()))
                    .build();
            Users saved = userRepository.save(newUser);
            setupNewUser(saved);
            return saved;
        } catch (Exception e) {
            log.error("Erro ao salvar usuário: ", e);
            throw new BadRequestException("Falha crítica", "Erro ao processar o cadastro no banco de dados.");
        }

    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Users> usersOptional = userRepository.findByEmailIgnoreCase(email);
        if (usersOptional.isEmpty()) {
            throw new BadRequestException("Falha de Login", "Email ou senha incorretos. Tente novamente");
        }
        Users user = usersOptional.get();
        if (!user.getEnabled() || !user.getAccountNonLocked()) {
            throw new BadRequestException("Acesso negado", "Usuário bloqueado");
        }
        return new UserAuthenticateDTO(user);
    }

    @Override
    public void changePassword(PasswordChangeDTO passwordChangeDTO) {
        Users currentUser = SecurityContextUtils.getCurrentUser();

        // Verify current password
        if (!passwordEncoder.matches(passwordChangeDTO.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadRequestException("Erro de autenticação", ConstsMessages.INVALID_CURRENT_PASSWORD);
        }

        // Update password
        currentUser.setPassword(passwordEncoder.encode(passwordChangeDTO.getNewPassword()));
        currentUser.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(currentUser);
    }

    private void setupNewUser(Users user) {
        long now = System.currentTimeMillis();
        Accounts wallet = Accounts.builder()
                .id(ID.generate())
                .name("Minha Carteira")
                .type(AccountType.WALLET)
                .institution("N/A")
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(true)
                .enabled(true)
                .user(user)
                .createdAt(now)
                .build();
        Accounts accountSaved = accountsService.save(wallet);

        // --- DESPESAS ---
        createDefaultCategory(user, "Alimentação", "DESPESA", "restaurant", "#FFCA28", now);
        createDefaultCategory(user, "Moradia", "DESPESA", "home", "#FF5252", now);
        createDefaultCategory(user, "Transporte", "DESPESA", "directions_car", "#42A5F5", now);
        createDefaultCategory(user, "Saúde", "DESPESA", "medical_services", "#66BB6A", now);
        createDefaultCategory(user, "Lazer", "DESPESA", "sports_esports", "#AB47BC", now);
        createDefaultCategory(user, "Educação", "DESPESA", "school", "#EC407A", now);
        // Novas Despesas Essenciais:
        createDefaultCategory(user, "Mercado", "DESPESA", "shopping_cart", "#FFA726", now); // Laranja
        createDefaultCategory(user, "Contas Fixas", "DESPESA", "receipt_long", "#8D6E63", now); // Marrom
        createDefaultCategory(user, "Vestuário", "DESPESA", "checkroom", "#26A69A", now); // Verde Água (Cabide)
        createDefaultCategory(user, "Pets", "DESPESA", "pets", "#795548", now); // Marrom Escuro (Patinha)
        // --- RECEITAS ---
        createDefaultCategory(user, "Salário", "RECEITA", "attach_money", "#00E676", now);
        createDefaultCategory(user, "Investimentos", "RECEITA", "trending_up", "#2979FF", now);
        //--- OUTROS ---
        createDefaultCategory(user, "Outros", "RECEITA", "category", "#BDBDBD", now);

    }

    private void createDefaultCategory(Users user, String name, String type, String icon, String color, long now) {
        categoryService.save(Category.builder()
                .id(ID.generate())
                .name(name)
                .categoryType(type)
                .enabled(true)
                .isSubCategory(false)
                .isDefault(true)
                .icon(icon)
                .color(color)
                .user(user)
                .createdAt(now)
                .build());
    }
}

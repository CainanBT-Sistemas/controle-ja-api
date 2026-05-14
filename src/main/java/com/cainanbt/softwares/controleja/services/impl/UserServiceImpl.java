package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UpdateProfileDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserUpdateTokenDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RoleEnum;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.services.VehicleService;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final CreditCardService creditCardService;
    private final TransactionService transactionService;
    private final VehicleService vehicleService;

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
        }).orElseThrow(() -> new BadRequestException(ConstsMessages.OOPS_TITLE, ConstsMessages.SYSTEM_CRITICAL_ERROR));
    }

    @Override
    public Users createNewUser(InsertUpdateUserDTO userDTO, HttpServletRequest request) {
        if (userRepository.findByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new BadRequestException(ConstsMessages.REGISTRATION_ERROR_TITLE, ConstsMessages.EMAIL_IN_USE);
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
                    .createdAt(DateUtils.getEpochNow())
                    .build();

            Users saved = userRepository.save(newUser);
            setupNewUser(saved);
            return saved;
        } catch (Exception e) {
            log.error("Erro ao salvar usuário: ", e);
            throw new BadRequestException(ConstsMessages.CRITICAL_ERROR_TITLE, ConstsMessages.DATABASE_SAVE_ERROR);
        }
    }

    @Override
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

    @Override
    public void changePassword(PasswordChangeDTO passwordChangeDTO) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        if (!passwordEncoder.matches(passwordChangeDTO.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_CURRENT_PASSWORD);
        }

        currentUser.setPassword(passwordEncoder.encode(passwordChangeDTO.getNewPassword()));
        currentUser.setUpdatedAt(DateUtils.getEpochNow());
        userRepository.save(currentUser);
    }

    @Override
    public Users updateProfile(UpdateProfileDTO dto) {
        Users currentUser = SecurityContextUtils.getCurrentUser();
        currentUser.setUsername(dto.getUsername());
        currentUser.setUpdatedAt(DateUtils.getEpochNow());

        return userRepository.save(currentUser);
    }

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
            userRepository.deleteInstallmentsByUserId(userId);
            userRepository.deleteInvoicesByUserId(userId);
            userRepository.deleteTransactionsByUserId(userId);
            userRepository.deleteCreditCardsByUserId(userId);
            userRepository.deleteVehiclesByUserId(userId);
            userRepository.deleteSubCategoriesByUserId(userId);
            userRepository.deleteCategoriesByUserId(userId);
            userRepository.deleteAccountsByUserId(userId);
            setupNewUser(u);
            return u;
        }
        throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.FAILURE_TO_FIND_USER);
    }

    private void setupNewUser(Users user) {
        long now = DateUtils.getEpochNow();

        Accounts wallet = Accounts.builder()
                .id(ID.generate())
                .name("Minha Carteira")
                .type(AccountType.WALLET)
                .institution("")
                .currency("BRL")
                .currentBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .calculateBalance(true)
                .enabled(true)
                .icon("account_balance_wallet")
                .color("#42A5F5")
                .isDefault(true)
                .user(user)
                .createdAt(now)
                .build();
        accountsService.save(wallet);

        createDefaultCategory(user, "Alimentação", TransactionType.DESPESA.name(), "restaurant", "#FFCA28", now, null);
        createDefaultCategory(user, "Moradia", TransactionType.DESPESA.name(), "home", "#FF5252", now, null);
        createDefaultCategory(user, "Transporte", TransactionType.DESPESA.name(), "directions_car", "#42A5F5", now, null);
        createDefaultCategory(user, "Saúde", TransactionType.DESPESA.name(), "medical_services", "#66BB6A", now, null);
        createDefaultCategory(user, "Lazer", TransactionType.DESPESA.name(), "sports_esports", "#AB47BC", now, null);
        createDefaultCategory(user, "Educação", TransactionType.DESPESA.name(), "school", "#EC407A", now, null);
        createDefaultCategory(user, "Mercado", TransactionType.DESPESA.name(), "shopping_cart", "#FFA726", now, null);
        createDefaultCategory(user, "Contas Fixas", TransactionType.DESPESA.name(), "receipt_long", "#8D6E63", now, null);
        createDefaultCategory(user, "Vestuário", TransactionType.DESPESA.name(), "checkroom", "#26A69A", now, null);
        createDefaultCategory(user, "Pets", TransactionType.DESPESA.name(), "pets", "#795548", now, null);

        Category categoryVeiculo = createDefaultCategory(user, "Veículo", TransactionType.DESPESA.name(), "directions_car", "#3F51B5", now, null);
        createDefaultCategory(user, "Abastecimento", TransactionType.DESPESA.name(), "local_gas_station", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Manutenção", TransactionType.DESPESA.name(), "build", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Seguro", TransactionType.DESPESA.name(), "health_and_safety", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Impostos (IPVA/Lic.)", TransactionType.DESPESA.name(), "receipt", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Multas", TransactionType.DESPESA.name(), "gavel", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Estacionamento/Pedágio", TransactionType.DESPESA.name(), "toll", "#3F51B5", now, categoryVeiculo);
        createDefaultCategory(user, "Estética", TransactionType.DESPESA.name(), "local_car_wash", "#3F51B5", now, categoryVeiculo);

        createDefaultCategory(user, "Reajuste de Saldo", TransactionType.REAJUSTE_SALDO.name(), "sync", "#9E9E9E", now, null);


        createDefaultCategory(user, "Salário", TransactionType.RECEITA.name(), "attach_money", "#00E676", now, null);
        createDefaultCategory(user, "Investimentos", TransactionType.RECEITA.name(), "trending_up", "#2979FF", now, null);

        createDefaultCategory(user, "Outros", TransactionType.RECEITA.name(), "category", "#BDBDBD", now, null);
    }

    private Category createDefaultCategory(Users user, String name, String type, String icon, String color, long now, Category parentCategory) {
        return categoryService.save(Category.builder()
                .id(ID.generate())
                .name(name)
                .categoryType(type)
                .enabled(true)
                .isSubCategory(parentCategory != null)
                .isDefault(true)
                .icon(icon)
                .color(color)
                .user(user)
                .createdAt(now)
                .subCategory(parentCategory)
                .build());
    }
}
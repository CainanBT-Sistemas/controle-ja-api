package com.cainanbt.softwares.controleja.services.entitlements;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementReconciliationServiceTest {

    private final AccountsRepository accountsRepository = mock(AccountsRepository.class);
    private final CreditCardRepository creditCardRepository = mock(CreditCardRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final UUID userId = UUID.randomUUID();
    private final Users user = Users.builder().id(userId).build();

    private EntitlementReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementReconciliationService(
                accountsRepository,
                creditCardRepository,
                categoryRepository,
                vehicleRepository
        );
    }

    @Test
    void shouldKeepOldestFreeResourcesAvailableAndRestrictOverflow() {
        List<Accounts> accounts = List.of(account(true), account(true), account(true), account(true));
        Accounts mirrorOne = account(true);
        Accounts mirrorTwo = account(true);
        Accounts mirrorThree = account(true);
        CreditCard firstCard = card(true, mirrorOne);
        CreditCard secondCard = card(true, mirrorTwo);
        CreditCard overflowCard = card(true, mirrorThree);
        Category firstParent = parent(true);
        List<Category> parents = List.of(firstParent, parent(true), parent(true), parent(true), parent(true), parent(true));
        List<Category> children = List.of(child(true, firstParent), child(true, firstParent), child(true, firstParent));
        List<Vehicle> vehicles = List.of(vehicle(), vehicle());

        when(accountsRepository.findFinancialAccountsForReconciliation(userId)).thenReturn(accounts);
        when(creditCardRepository.findForReconciliation(userId))
                .thenReturn(List.of(firstCard, secondCard, overflowCard));
        when(categoryRepository.findCustomParentsForReconciliation(userId)).thenReturn(parents);
        when(categoryRepository.findCustomChildrenForReconciliation(firstParent.getId())).thenReturn(children);
        for (Category parent : parents.subList(1, parents.size())) {
            when(categoryRepository.findCustomChildrenForReconciliation(parent.getId())).thenReturn(List.of());
        }
        when(vehicleRepository.findForReconciliation(userId)).thenReturn(vehicles);

        service.reconcile(user, EffectivePlan.FREE);

        assertFalse(accounts.get(0).getPlanRestricted());
        assertFalse(accounts.get(2).getPlanRestricted());
        assertTrue(accounts.get(3).getPlanRestricted());
        assertFalse(firstCard.getPlanRestricted());
        assertTrue(secondCard.getPlanRestricted());
        assertTrue(overflowCard.getPlanRestricted());
        assertFalse(mirrorOne.getPlanRestricted());
        assertTrue(mirrorTwo.getPlanRestricted());
        assertTrue(mirrorThree.getPlanRestricted());
        assertFalse(parents.get(4).getPlanRestricted());
        assertTrue(parents.get(5).getPlanRestricted());
        assertFalse(children.get(1).getPlanRestricted());
        assertTrue(children.get(2).getPlanRestricted());
        assertTrue(vehicles.get(0).getPlanRestricted());
        assertTrue(vehicles.get(1).getPlanRestricted());
    }

    @Test
    void shouldRemoveOnlyPlanRestrictionOnUpgrade() {
        Accounts disabledAccount = account(false);
        disabledAccount.setPlanRestricted(true);
        Vehicle deletedVehicle = vehicle();
        deletedVehicle.setDeletedAt(123L);
        deletedVehicle.setPlanRestricted(true);

        when(accountsRepository.findFinancialAccountsForReconciliation(userId)).thenReturn(List.of(disabledAccount));
        when(creditCardRepository.findForReconciliation(userId)).thenReturn(List.of());
        when(categoryRepository.findCustomParentsForReconciliation(userId)).thenReturn(List.of());
        when(vehicleRepository.findForReconciliation(userId)).thenReturn(List.of(deletedVehicle));

        service.reconcile(user, EffectivePlan.PLUS);

        assertFalse(disabledAccount.getPlanRestricted());
        assertFalse(disabledAccount.getEnabled());
        assertFalse(deletedVehicle.getPlanRestricted());
        assertTrue(deletedVehicle.getDeletedAt() != null);
    }

    private Accounts account(boolean enabled) {
        return Accounts.builder()
                .id(UUID.randomUUID())
                .type(AccountType.BANK)
                .enabled(enabled)
                .planRestricted(false)
                .build();
    }

    private CreditCard card(boolean enabled, Accounts mirror) {
        return CreditCard.builder()
                .id(UUID.randomUUID())
                .enabled(enabled)
                .accounts(mirror)
                .planRestricted(false)
                .build();
    }

    private Category parent(boolean enabled) {
        return Category.builder()
                .id(UUID.randomUUID())
                .enabled(enabled)
                .isDefault(false)
                .isSubCategory(false)
                .planRestricted(false)
                .build();
    }

    private Category child(boolean enabled, Category parent) {
        return Category.builder()
                .id(UUID.randomUUID())
                .enabled(enabled)
                .isDefault(false)
                .isSubCategory(true)
                .subCategory(parent)
                .planRestricted(false)
                .build();
    }

    private Vehicle vehicle() {
        return Vehicle.builder()
                .id(UUID.randomUUID())
                .planRestricted(false)
                .build();
    }
}

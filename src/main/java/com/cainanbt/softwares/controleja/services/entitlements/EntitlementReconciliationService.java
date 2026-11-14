package com.cainanbt.softwares.controleja.services.entitlements;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.CategoryRepository;
import com.cainanbt.softwares.controleja.repositories.CreditCardRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EntitlementReconciliationService {

    private final AccountsRepository accountsRepository;
    private final CreditCardRepository creditCardRepository;
    private final CategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional
    public void reconcile(Users user, EffectivePlan plan) {
        PlanLimits limits = plan.getLimits();

        List<Accounts> accounts = accountsRepository.findFinancialAccountsForReconciliation(user.getId());
        int availableAccounts = 0;
        for (Accounts account : accounts) {
            boolean restricted = Boolean.TRUE.equals(account.getEnabled()) && availableAccounts++ >= limits.accounts();
            account.setPlanRestricted(restricted);
        }
        accountsRepository.saveAll(accounts);

        List<CreditCard> cards = creditCardRepository.findForReconciliation(user.getId());
        int availableCards = 0;
        for (CreditCard card : cards) {
            boolean restricted = Boolean.TRUE.equals(card.getEnabled()) && availableCards++ >= limits.creditCards();
            card.setPlanRestricted(restricted);
            card.getAccounts().setPlanRestricted(restricted);
        }
        creditCardRepository.saveAll(cards);

        List<Category> parents = categoryRepository.findCustomParentsForReconciliation(user.getId());
        int availableParents = 0;
        for (Category parent : parents) {
            boolean restricted = Boolean.TRUE.equals(parent.getEnabled()) && availableParents++ >= limits.categories();
            parent.setPlanRestricted(restricted);

            List<Category> children = categoryRepository.findCustomChildrenForReconciliation(parent.getId());
            int availableChildren = 0;
            for (Category child : children) {
                boolean childRestricted = Boolean.TRUE.equals(child.getEnabled())
                        && availableChildren++ >= limits.subcategoriesPerParent();
                child.setPlanRestricted(childRestricted);
            }
            categoryRepository.saveAll(children);
        }
        categoryRepository.saveAll(parents);

        List<Vehicle> vehicles = vehicleRepository.findForReconciliation(user.getId());
        for (int index = 0; index < vehicles.size(); index++) {
            vehicles.get(index).setPlanRestricted(index >= limits.vehicles());
        }
        vehicleRepository.saveAll(vehicles);
    }
}

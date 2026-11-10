package com.cainanbt.softwares.controleja.services.entitlements;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitlementMutationGuard {

    private final UsersRepository usersRepository;
    private final EntitlementService entitlementService;
    private final EntitlementReconciliationService reconciliationService;

    public PlanLimits touchAndGetLimits(Users principal) {
        Users user = findUser(principal);
        long currentTouch = user.getEntitlementTouchedAt() == null ? 0L : user.getEntitlementTouchedAt();
        user.setEntitlementTouchedAt(Math.max(DateUtils.getEpochNow(), currentTouch + 1));
        usersRepository.saveAndFlush(user);
        var plan = entitlementService.resolvePlan(user);
        reconciliationService.reconcile(user, plan);
        return plan.getLimits();
    }

    public void reconcileForMutation(Users principal) {
        Users user = findUser(principal);
        reconciliationService.reconcile(user, entitlementService.resolvePlan(user));
    }

    public void requireCapacity(long currentCount, int limit, String resourceName) {
        if (currentCount >= limit) {
            throw new BadRequestException(
                    "Limite atingido",
                    "O limite de " + resourceName + " do plano atual foi atingido. Use o Plus ou reduza os itens ativos."
            );
        }
    }

    public void requireAvailable(Accounts account) {
        requireNotRestricted(account != null && Boolean.TRUE.equals(account.getPlanRestricted()));
    }

    public void requireAvailable(CreditCard card) {
        requireNotRestricted(card != null && Boolean.TRUE.equals(card.getPlanRestricted()));
    }

    public void requireAvailable(Category category) {
        boolean restricted = category != null && (Boolean.TRUE.equals(category.getPlanRestricted())
                || category.getSubCategory() != null
                && Boolean.TRUE.equals(category.getSubCategory().getPlanRestricted()));
        requireNotRestricted(restricted);
    }

    public void requireAvailable(Vehicle vehicle) {
        requireNotRestricted(vehicle != null && Boolean.TRUE.equals(vehicle.getPlanRestricted()));
    }

    private void requireNotRestricted(boolean restricted) {
        if (restricted) {
            throw new BadRequestException(
                    "Recurso indisponível no plano",
                    "Este recurso excede o plano atual. Use o Plus ou reduza os itens ativos."
            );
        }
    }

    private Users findUser(Users principal) {
        return usersRepository.findById(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Erro", "Usuário não encontrado."));
    }
}

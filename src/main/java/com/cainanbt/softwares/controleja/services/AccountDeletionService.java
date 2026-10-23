package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.springframework.stereotype.Service;

@Service
public class AccountDeletionService {
    public void deactivate(Users user) {
        user.setDeletedAt(DateUtils.getEpochNow());
        user.setEnabled(false);
        user.setAccountNonLocked(false);
        invalidateSession(user);
    }

    public void reactivate(Users user) {
        user.setDeletedAt(null);
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setUpdatedAt(DateUtils.getEpochNow());
        invalidateSession(user);
    }

    private void invalidateSession(Users user) {
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(0L);
        user.setSessionVersion(user.getSessionVersion() + 1L);
    }
}

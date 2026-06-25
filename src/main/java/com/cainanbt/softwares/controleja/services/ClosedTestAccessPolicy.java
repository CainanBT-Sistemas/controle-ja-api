package com.cainanbt.softwares.controleja.services;

public interface ClosedTestAccessPolicy {

    boolean isEnabled();

    boolean isAllowlisted(String email);

    boolean isAccessAllowed(String email);

    void requireAccess(String email);
}

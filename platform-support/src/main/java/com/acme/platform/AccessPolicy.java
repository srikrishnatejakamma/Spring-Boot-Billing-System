package com.acme.platform;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

public final class AccessPolicy {
    private final boolean secured;
    public AccessPolicy(boolean secured) { this.secured = secured; }
    public boolean allows(String scope) {
        return allows(SecurityContextHolder.getContext().getAuthentication(), scope);
    }
    public boolean allows(Authentication authentication, String scope) {
        if (!secured) return true;
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + scope));
    }
    public void require(String scope) {
        if (!allows(scope)) throw new AccessDeniedException("Required scope: " + scope);
    }
    public String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? (secured ? "system" : "demo-operator") : authentication.getName();
    }
    public boolean secured() { return secured; }
}

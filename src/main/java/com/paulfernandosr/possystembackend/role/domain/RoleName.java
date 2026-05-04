package com.paulfernandosr.possystembackend.role.domain;

public enum RoleName {
    ADMINISTRATOR(false),
    CASHIER(true),
    WHOLESALER(false),
    WAREHOUSE(false);

    private final boolean requiresCashSession;

    RoleName(boolean requiresCashSession) {
        this.requiresCashSession = requiresCashSession;
    }

    public boolean requiresCashSession() {
        return requiresCashSession;
    }
}
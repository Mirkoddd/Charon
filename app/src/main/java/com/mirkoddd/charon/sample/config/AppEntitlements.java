package com.mirkoddd.charon.sample.config;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.catalog.CharonEntitlement;

public enum AppEntitlements implements CharonEntitlement {
    PREMIUM_MOTO("premium_moto");
    private final String id;

    AppEntitlements(String id) {
        this.id = id;
    }

    @NonNull
    @Override
    public String id() {
        return id;
    }
}

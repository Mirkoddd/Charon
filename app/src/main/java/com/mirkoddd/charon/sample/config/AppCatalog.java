package com.mirkoddd.charon.sample.config;

import androidx.annotation.NonNull;

import com.mirkoddd.charon.annotations.Consumable;
import com.mirkoddd.charon.annotations.NonConsumable;
import com.mirkoddd.charon.catalog.CharonSkuIdentity;

public enum AppCatalog implements CharonSkuIdentity {
    @Consumable
    REFILL_TANK("refill_gas"),

    @NonConsumable(
            entitlements = "premium_moto"
    )
    BUY_MOTO("buy_motorcycle");

    private final String skuId;

    AppCatalog(String skuId) {
        this.skuId = skuId;
    }

    @Override
    @NonNull
    public String id() {
        return skuId;
    }

}

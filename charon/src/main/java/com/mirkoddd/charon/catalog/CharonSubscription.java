package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Optional;

public record CharonSubscription(
        @NonNull String id,
        @NonNull String title,
        @NonNull String description,
        @NonNull List<CharonPlan> plans) {

    @NonNull
    public Optional<CharonPlan> bestPlan(@NonNull BillingPeriod period) {
        return plans.stream()
                .filter(p -> p.pricing().billingPeriod() == period)
                .findFirst(); 
    }

    public SkuType type() {
        return SkuType.SUBSCRIPTION;
    }

    @NonNull
    @Override
    public String toString() {
        return "CharonSubscription{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", plans=" + plans +
                '}';
    }
}

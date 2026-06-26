package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.mirkoddd.charon.CharonSku;

public record CharonPlan(
        @NonNull CharonSku skuConfig,
        @NonNull String basePlanId,
        @Nullable String offerId,
        @NonNull String offerToken,
        @NonNull Pricing pricing) implements CharonPurchaseOption {

    @NonNull
    @Override
    public CharonOffer.Category category() {
        return CharonOffer.Category.SUBSCRIPTION;
    }

    @NonNull
    @Override
    public PricingProvider pricingInfo() {
        return new PricingProvider() {
            @Override @NonNull public String currentPrice() { return pricing.currentPrice(); }
            @Override public long amountMicros() { 
                // We don't have micros in subscription pricing yet, using a sentinel or placeholder
                return 0; 
            }
        };
    }

    public sealed interface Pricing permits FreeTrial, Discounted, Standard {
        @NonNull String currentPrice();
        @NonNull BillingPeriod billingPeriod();
    }

    public record FreeTrial(
            @NonNull String price,
            @NonNull String renewalPrice,
            @NonNull BillingPeriod billingPeriod,
            @NonNull BillingPeriod trialPeriod) implements Pricing {
        @Override @NonNull public String currentPrice() { return price; }
    }

    public record Discounted(
            @NonNull String price,
            @NonNull String fullPrice,
            @NonNull BillingPeriod billingPeriod) implements Pricing {
        @Override @NonNull public String currentPrice() { return price; }
    }

    public record Standard(
            @NonNull String price,
            @NonNull BillingPeriod billingPeriod) implements Pricing {
        @Override @NonNull public String currentPrice() { return price; }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public CharonPlan {
    }

    @NonNull
    public String productId() {
        return skuConfig.id();
    }

    @Nullable
    public String groupId() {
        return skuConfig.groupId();
    }
}

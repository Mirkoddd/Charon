package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.CharonSku;

public record CharonOffer(
        @NonNull CharonSku skuConfig,
        @NonNull String purchaseOptionId,
        @Nullable String offerId,
        @NonNull String offerToken,
        @NonNull Pricing pricing,
        @NonNull Mode mode) implements CharonPurchaseOption {

    public enum Category {
        PURCHASE,
        RENTAL,
        LIMITED,
        SUBSCRIPTION
    }

    @NonNull
    @Override
    public Category category() {
        if (mode instanceof Rental) return Category.RENTAL;
        if (mode instanceof Limited) return Category.LIMITED;
        return Category.PURCHASE;
    }

    @NonNull
    @Override
    public PricingProvider pricingInfo() {
        return new PricingProvider() {
            @Override @NonNull public String currentPrice() { return pricing.currentPrice(); }
            @Override public long amountMicros() { return pricing.amountMicros(); }
        };
    }

    public sealed interface Pricing permits Standard, Discounted {
        @NonNull String currentPrice();
        long amountMicros();
    }

    public record Standard(@NonNull String price, long amountMicros) implements Pricing {
        @Override @NonNull public String currentPrice() { return price; }
        @Override public long amountMicros() { return amountMicros; }
    }

    public record Discounted(
            @NonNull String price,
            long amountMicros,
            @NonNull String fullPrice,
            @NonNull Discount info,
            @Nullable TimeWindow validity) implements Pricing {
        @Override @NonNull public String currentPrice() { return price; }
        @Override public long amountMicros() { return amountMicros; }
    }

    public record Discount(int percentage, @Nullable String formattedAmount, long micros, @Nullable String currencyCode) {}

    public record TimeWindow(long startMillis, long endMillis) {}

    public sealed interface Mode permits Purchase, Limited, Rental {
    }

    public record Purchase() implements Mode {
    }

    public record Limited(int max, int remaining) implements Mode {
    }

    public record Rental(@NonNull String period, @NonNull String startPeriod) implements Mode {
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public CharonOffer {
    }

    @NonNull
    public String productId() {
        return skuConfig.id();
    }
}

package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record CharonInApp(
        @NonNull String id,
        @NonNull String title,
        @NonNull String description,
        @NonNull List<CharonOffer> offers,
        @NonNull SkuType type) {

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public CharonInApp {
    }

    @NonNull
    public CharonOffer standardOffer() {
        return offers.stream()
                .filter(o -> o.offerId() == null)
                .findFirst()
                .orElse(offers.get(0));
    }

    @NonNull
    public java.util.Optional<CharonOffer> bestOffer() {
        return offers.stream()
                .filter(o -> o.offerId() != null)
                .min(java.util.Comparator.comparingLong(o -> o.pricing().amountMicros()));
    }

    @NonNull
    public java.util.Optional<CharonOffer> bestOffer(@NonNull CharonOffer.Category category) {
        return offers.stream()
                .filter(o -> o.category() == category)
                .min(java.util.Comparator.comparingLong(o -> o.pricing().amountMicros()));
    }

    @NonNull
    public CharonOffer bestOfferOrStandard() {
        return bestOffer().orElseGet(this::standardOffer);
    }
}

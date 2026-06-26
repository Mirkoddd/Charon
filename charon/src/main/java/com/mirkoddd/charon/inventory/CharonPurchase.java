package com.mirkoddd.charon.inventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import java.util.List;

public record CharonPurchase(
        @Nullable String orderId,
        @NonNull List<String> skus,
        @NonNull String purchaseToken,
        long purchaseTime,
        int quantity,
        boolean acknowledged,
        boolean autoRenewing,
        boolean isSubscription,
        @NonNull String originalJson,
        @NonNull String signature,
        @Nullable String accountId,
        @Nullable String profileId) {

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public CharonPurchase {
        skus = List.copyOf(skus);
    }
}

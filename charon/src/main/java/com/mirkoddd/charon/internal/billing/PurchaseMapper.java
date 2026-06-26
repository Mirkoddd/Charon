package com.mirkoddd.charon.internal.billing;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.Purchase;
import com.mirkoddd.charon.inventory.CharonPurchase;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    @NonNull
    public static CharonPurchase map(@NonNull Purchase p, boolean isSubscription) {
        String accountId = null;
        String profileId = null;
        if (p.getAccountIdentifiers() != null) {
            accountId = p.getAccountIdentifiers().getObfuscatedAccountId();
            profileId = p.getAccountIdentifiers().getObfuscatedProfileId();
        }

        return new CharonPurchase(
                p.getOrderId(),
                p.getProducts(),
                p.getPurchaseToken(),
                p.getPurchaseTime(),
                p.getQuantity(),
                p.isAcknowledged(),
                p.isAutoRenewing(),
                isSubscription,
                p.getOriginalJson(),
                p.getSignature(),
                accountId,
                profileId
        );
    }
}

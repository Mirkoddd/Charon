package com.mirkoddd.charon.internal.billing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.checkout.internal.Security;
import com.mirkoddd.charon.inventory.CharonPurchase;

import java.util.Objects;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PurchaseBouncer {

    private PurchaseBouncer() {
    }

    public static boolean isSignatureValid(@NonNull CharonPurchase cp, @NonNull CharonConfiguration config) {
        String licenseKey = config.getLicenseKey();
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return true;
        }
        return Security.verifyPurchase(licenseKey, cp.originalJson(), cp.signature());
    }

    public static boolean isPurchaseMine(@NonNull CharonPurchase cp, @Nullable String currentAccountId) {
        String receiptId = cp.accountId();
        boolean isReceiptAnonymous = (receiptId == null || receiptId.isBlank());
        boolean isSessionAnonymous = (currentAccountId == null || currentAccountId.isBlank());

        if (isSessionAnonymous) {
            return isReceiptAnonymous;
        }

        return Objects.equals(receiptId, currentAccountId) || isReceiptAnonymous;
    }
}

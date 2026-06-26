package com.mirkoddd.charon.inventory;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mirkoddd.charon.catalog.CharonSkuIdentity;

public record CharonInventory(
    @NonNull Set<String> activeSkus,
    @NonNull Set<String> activeEntitlements,
    @NonNull Map<String, String> groupTokens,
    @NonNull List<CharonPurchase> purchaseHistory,
    @NonNull List<CharonPurchase> pendingPurchases,
    @NonNull List<CharonPurchase> unfulfilledPurchases,
    @NonNull List<CharonPurchase> rejectedPurchases,
    @NonNull List<CharonPurchase> networkErrorPurchases
) {
    public CharonInventory {
        activeSkus = Set.copyOf(activeSkus);
        activeEntitlements = Set.copyOf(activeEntitlements);
        groupTokens = Map.copyOf(groupTokens);
        purchaseHistory = List.copyOf(purchaseHistory);
        pendingPurchases = List.copyOf(pendingPurchases);
        unfulfilledPurchases = List.copyOf(unfulfilledPurchases);
        rejectedPurchases = List.copyOf(rejectedPurchases);
        networkErrorPurchases = List.copyOf(networkErrorPurchases);
    }

    public static CharonInventory empty() {
        return new CharonInventory(Set.of(), Set.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public String tokenForGroup(String groupId) {
        return groupTokens.get(groupId);
    }

    public boolean hasEntitlement(@NonNull com.mirkoddd.charon.catalog.CharonEntitlement entitlement) {
        return activeEntitlements.contains(entitlement.id());
    }

    @NonNull
    public PurchaseStatus statusFor(@NonNull CharonSkuIdentity sku) {
        return statusFor(sku.id());
    }

    @NonNull
    public PurchaseStatus statusFor(@NonNull String productId) {
        for (var p : networkErrorPurchases) {
            if (p.skus().contains(productId)) {
                return PurchaseStatus.VALIDATION_FAILED;
            }
        }

        for (var p : rejectedPurchases) {
            if (p.skus().contains(productId)) {
                return PurchaseStatus.REJECTED;
            }
        }

        for (var p : unfulfilledPurchases) {
            if (p.skus().contains(productId)) {
                return PurchaseStatus.VALIDATING;
            }
        }

        if (activeSkus.contains(productId)) {
            boolean isAutoRenewing = true;
            boolean isSubscription = false;
            for (var p : purchaseHistory) {
                if (p.skus().contains(productId)) {
                    isAutoRenewing = p.autoRenewing();
                    isSubscription = p.isSubscription();
                    break;
                }
            }
            if (isSubscription && !isAutoRenewing) {
                return PurchaseStatus.ACTIVE_NON_RENEWING;
            }
            return PurchaseStatus.ACTIVE;
        }
        
        for (var p : pendingPurchases) {
            if (p.skus().contains(productId)) {
                return PurchaseStatus.PENDING_PURCHASE;
            }
        }
        
        return PurchaseStatus.AVAILABLE;
    }
}

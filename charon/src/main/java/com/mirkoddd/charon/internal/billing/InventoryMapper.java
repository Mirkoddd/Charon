package com.mirkoddd.charon.internal.billing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.Purchase;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.android.billingclient.api.PurchaseHistoryRecord;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.catalog.CharonOffer;
import java.time.Duration;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class InventoryMapper {

    private InventoryMapper() {
    }

    @NonNull
    public static CharonInventory map(
            @Nullable List<Purchase> subPurchases,
            @Nullable List<Purchase> inAppPurchases,
            @NonNull Set<String> rejectedTokens,
            @NonNull Set<String> networkErrorTokens,
            @NonNull Set<CharonPurchase> validatingPurchases,
            @NonNull CharonConfiguration configuration,
            @Nullable String currentAccountId) {

        Set<String> activeSkus = new HashSet<>();
        Set<String> activeEntitlements = new HashSet<>();
        Map<String, String> groupTokens = new HashMap<>();
        List<CharonPurchase> allPurchases = new ArrayList<>();
        List<CharonPurchase> pendingPurchases = new ArrayList<>();
        List<CharonPurchase> unfulfilledPurchases = new ArrayList<>();
        List<CharonPurchase> rejectedPurchases = new ArrayList<>();
        List<CharonPurchase> networkErrorPurchases = new ArrayList<>();

        Set<String> seenTokens = new HashSet<>();

        if (subPurchases != null) {
            for (Purchase p : subPurchases) {
                seenTokens.add(p.getPurchaseToken());
                CharonPurchase cp = PurchaseMapper.map(p, true);
                processMappedPurchase(p, cp, configuration, currentAccountId, allPurchases, activeSkus, activeEntitlements, groupTokens, rejectedTokens, networkErrorTokens, rejectedPurchases, networkErrorPurchases, unfulfilledPurchases, pendingPurchases);
            }
        }

        if (inAppPurchases != null) {
            for (Purchase p : inAppPurchases) {
                seenTokens.add(p.getPurchaseToken());
                CharonPurchase cp = PurchaseMapper.map(p, false);
                processMappedPurchase(p, cp, configuration, currentAccountId, allPurchases, activeSkus, activeEntitlements, groupTokens, rejectedTokens, networkErrorTokens, rejectedPurchases, networkErrorPurchases, unfulfilledPurchases, pendingPurchases);
            }
        }

        for (CharonPurchase vp : validatingPurchases) {
            if (!seenTokens.contains(vp.purchaseToken())) {
                allPurchases.add(vp);
                if (rejectedTokens.contains(vp.purchaseToken())) {
                    rejectedPurchases.add(vp);
                } else if (networkErrorTokens.contains(vp.purchaseToken())) {
                    networkErrorPurchases.add(vp);
                } else {
                    unfulfilledPurchases.add(vp);
                }
            }
        }

        return new CharonInventory(activeSkus, activeEntitlements, groupTokens, allPurchases, pendingPurchases, unfulfilledPurchases, rejectedPurchases, networkErrorPurchases);
    }

    private static void processMappedPurchase(
            @NonNull Purchase p,
            @NonNull CharonPurchase cp,
            @NonNull CharonConfiguration configuration,
            @Nullable String currentAccountId,
            @NonNull List<CharonPurchase> allPurchases,
            @NonNull Set<String> activeSkus,
            @NonNull Set<String> activeEntitlements,
            @NonNull Map<String, String> groupTokens,
            @NonNull Set<String> rejectedTokens,
            @NonNull Set<String> networkErrorTokens,
            @NonNull List<CharonPurchase> rejectedPurchases,
            @NonNull List<CharonPurchase> networkErrorPurchases,
            @NonNull List<CharonPurchase> unfulfilledPurchases,
            @NonNull List<CharonPurchase> pendingPurchases) {

        if (PurchaseBouncer.isSignatureValid(cp, configuration) && PurchaseBouncer.isPurchaseMine(cp, currentAccountId)) {
            allPurchases.add(cp);
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                updateInventoryState(p, configuration, activeSkus, activeEntitlements, groupTokens);

                if (!p.isAcknowledged()) {
                    if (rejectedTokens.contains(p.getPurchaseToken())) {
                        rejectedPurchases.add(cp);
                    } else if (networkErrorTokens.contains(p.getPurchaseToken())) {
                        networkErrorPurchases.add(cp);
                    } else {
                        unfulfilledPurchases.add(cp);
                    }
                }
            } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                pendingPurchases.add(cp);
            }
        }
    }

    private static void updateInventoryState(@NonNull Purchase p, @NonNull CharonConfiguration config, @NonNull Set<String> skus, @NonNull Set<String> entitlements, @NonNull Map<String, String> tokens) {
        for (String productId : p.getProducts()) {
            skus.add(productId);
            CharonSku skuConfig = config.getSku(productId);
            if (skuConfig != null) {
                entitlements.addAll(skuConfig.entitlements());
                if (skuConfig.groupId() != null) {
                    tokens.put(skuConfig.groupId(), p.getPurchaseToken());
                }
            }
        }
    }
}

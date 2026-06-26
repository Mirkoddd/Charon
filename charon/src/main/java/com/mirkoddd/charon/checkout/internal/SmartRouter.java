package com.mirkoddd.charon.checkout.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.checkout.CharonReplacementMode;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class SmartRouter {

    private SmartRouter() {
    }

    public record RoutingDecision(@NonNull String oldProductId, @NonNull String oldToken, @NonNull CharonReplacementMode mode) {
    }

    @Nullable
    public static RoutingDecision calculateRouting(
            @NonNull CharonInventory inventory,
            @NonNull CharonConfiguration configuration,
            @NonNull com.mirkoddd.charon.catalog.CharonPlan targetPlan) {

        var targetSku = targetPlan.skuConfig();
        var groupId = targetSku.groupId();
        if (groupId == null || groupId.isBlank()) {
            return null;
        }

        var oldToken = inventory.tokenForGroup(groupId);
        if (oldToken == null || oldToken.isEmpty()) {
            return null;
        }

        var oldSku = findActiveSkuInGroup(inventory, configuration, groupId);
        if (oldSku == null) {
            return null;
        }

        int weightCompare = Integer.compare(targetSku.weight(), oldSku.weight());

        var mode = switch (weightCompare) {
            case 1 -> switch (targetSku.upgradeMode()) {
                case TIME_PRORATION -> CharonReplacementMode.WITH_TIME_PRORATION;
                case CHARGE_PRORATED_PRICE -> CharonReplacementMode.CHARGE_PRORATED_PRICE;
                case CHARGE_FULL_PRICE -> CharonReplacementMode.CHARGE_FULL_PRICE;
            };
            case -1 -> CharonReplacementMode.DEFERRED;
            default -> CharonReplacementMode.WITHOUT_PRORATION;
        };

        return new RoutingDecision(oldSku.id(), oldToken, mode);
    }

    @Nullable
    private static CharonSku findActiveSkuInGroup(
            @NonNull CharonInventory inventory,
            @NonNull CharonConfiguration configuration,
            @NonNull String groupId) {

        CharonSku bestMatch = null;

        for (var ownedSkuId : inventory.activeSkus()) {
            var sku = configuration.getSku(ownedSkuId);
            if (sku != null && groupId.equals(sku.groupId())) {
                if (bestMatch == null || sku.weight() > bestMatch.weight()) {
                    bestMatch = sku;
                }
            }
        }
        return bestMatch;
    }
}

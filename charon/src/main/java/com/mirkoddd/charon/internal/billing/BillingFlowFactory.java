package com.mirkoddd.charon.internal.billing;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.mirkoddd.charon.checkout.CharonReplacementMode;
import com.mirkoddd.charon.internal.engine.CharonLogger;

import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class BillingFlowFactory {

    private BillingFlowFactory() {
    }

    public static boolean launch(
            @NonNull Activity activity,
            @NonNull BillingClient client,
            @NonNull ProductDetails details,
            @Nullable String accountId,
            @Nullable String profileId,
            @Nullable String oldProductId,
            @Nullable String oldToken,
            @Nullable String offerToken,
            @Nullable CharonReplacementMode mode,
            @NonNull CharonLogger logger) {

        var productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details);

        if (offerToken != null) {
            productParamsBuilder.setOfferToken(offerToken);
        }

        if (mode != null && oldProductId != null) {
            var replacementParams = BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.newBuilder()
                    .setReplacementMode(resolveReplacementMode(mode))
                    .setOldProductId(oldProductId)
                    .build();
            productParamsBuilder.setSubscriptionProductReplacementParams(replacementParams);
        }

        var flowParamsBuilder = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(List.of(productParamsBuilder.build()));

        if (accountId != null) {
            flowParamsBuilder.setObfuscatedAccountId(accountId);
        }

        if (profileId != null) {
            flowParamsBuilder.setObfuscatedProfileId(profileId);
        }

        if (oldToken != null) {
            var updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldToken)
                    .build();
            flowParamsBuilder.setSubscriptionUpdateParams(updateParams);
        }

        BillingFlowParams finalParams = flowParamsBuilder.build();

        logger.log("Launching Flow: Product=" + details.getProductId() + ", Mode=" + mode);

        BillingResult result = client.launchBillingFlow(activity, finalParams);

        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            logger.error("Billing Flow Launch Failed: " + result.getResponseCode() + " - " + result.getDebugMessage());
            return false;
        }
        return true;
    }

    private static int resolveReplacementMode(CharonReplacementMode mode) {
        return switch (mode) {
            case CHARGE_FULL_PRICE -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE;
            case CHARGE_PRORATED_PRICE -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE;
            case DEFERRED -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.DEFERRED;
            case WITHOUT_PRORATION -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION;
            case WITH_TIME_PRORATION -> BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION;
        };
    }
}
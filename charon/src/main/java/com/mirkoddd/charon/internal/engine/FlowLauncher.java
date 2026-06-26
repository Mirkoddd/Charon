package com.mirkoddd.charon.internal.engine;

import com.android.billingclient.api.ProductDetails;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.catalog.CharonPlan;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.checkout.CharonReplacementMode;
import com.mirkoddd.charon.checkout.internal.SmartRouter;
import com.mirkoddd.charon.internal.billing.BillingFlowFactory;

import java.util.List;
import java.util.function.Consumer;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class FlowLauncher {

    private final BillingBridge billingBridge;
    private final PurchaseStore purchaseStore;
    private final CharonConfiguration configuration;
    private final IdentityManager identityManager;
    private final CharonLogger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Consumer<CharonError> errorReporter;
    private java.util.function.BiConsumer<String, com.mirkoddd.charon.catalog.CharonOffer.Mode> modeRegistrar;

    public FlowLauncher(@NonNull BillingBridge billingBridge,
                        @NonNull PurchaseStore purchaseStore,
                        @NonNull CharonConfiguration configuration,
                        @NonNull IdentityManager identityManager,
                        @NonNull CharonLogger logger) {
        this.billingBridge = billingBridge;
        this.purchaseStore = purchaseStore;
        this.configuration = configuration;
        this.identityManager = identityManager;
        this.logger = logger;
    }

    public void setErrorReporter(Consumer<CharonError> errorReporter) {
        this.errorReporter = errorReporter;
    }

    public void setModeRegistrar(java.util.function.BiConsumer<String, com.mirkoddd.charon.catalog.CharonOffer.Mode> modeRegistrar) {
        this.modeRegistrar = modeRegistrar;
    }

    public void launchInApp(@NonNull Activity activity, @NonNull CharonOffer offer) {
        billingBridge.showInAppMessages(activity);
        billingBridge.executeWhenReady(client -> {
            IdentitySession session = identityManager.getCurrentSession();
            queryAndLaunch(activity, client, offer, offer.productId(), BillingClient.ProductType.INAPP, 
                           session.accountId(), session.profileId(), null, null, null, null);
        }, this::dispatchError);
    }

    public void launchSubscription(@NonNull Activity activity, @NonNull CharonPlan plan) {
        billingBridge.showInAppMessages(activity);
        billingBridge.executeWhenReady(client -> {
            SmartRouter.RoutingDecision decision = SmartRouter.calculateRouting(
                    purchaseStore.getLastKnownInventory(),
                    configuration,
                    plan
            );

            IdentitySession session = identityManager.getCurrentSession();
            if (decision != null) {
                String actionName = switch (decision.mode()) {
                    case DEFERRED -> "Downgrade";
                    case WITH_TIME_PRORATION, CHARGE_FULL_PRICE, CHARGE_PRORATED_PRICE -> "Upgrade";
                    default -> "Crossgrade";
                };
                logger.log("Routing Decision: " + actionName + " detected! Mode: " + decision.mode());
                queryAndLaunch(activity, client, null, plan.productId(), BillingClient.ProductType.SUBS, 
                               session.accountId(), session.profileId(), decision.oldProductId(), decision.oldToken(), plan.offerToken(), decision.mode());
            } else {
                logger.log("Routing Decision: New subscription detected. No replacement needed.");
                queryAndLaunch(activity, client, null, plan.productId(), BillingClient.ProductType.SUBS, 
                               session.accountId(), session.profileId(), null, null, plan.offerToken(), null);
            }
        }, this::dispatchError);
    }

    private void queryAndLaunch(Activity activity, BillingClient client, @Nullable CharonOffer offer, 
                                String productId, String productType,
                                @Nullable String accountId, @Nullable String profileId,
                                @Nullable String oldProductId, @Nullable String oldToken, @Nullable String offerToken,
                                @Nullable CharonReplacementMode replacementMode) {

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(List.of(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()))
                .build();

        client.queryProductDetailsAsync(params, (result, productDetailsResult) -> {
            List<ProductDetails> detailsList = productDetailsResult.getProductDetailsList();
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && !detailsList.isEmpty()) {
                mainHandler.post(() -> {
                    boolean success = BillingFlowFactory.launch(activity, client, detailsList.get(0), accountId, profileId, 
                                                              oldProductId, oldToken, offerToken, replacementMode, logger);
                    if (success && offer != null && modeRegistrar != null) {
                        modeRegistrar.accept(productId, offer.mode());
                    }
                });
            } else {
                dispatchError(new CharonError(result.getResponseCode(), "Product details not found"));
            }
        });
    }

    private void dispatchError(CharonError error) {
        if (errorReporter != null) {
            mainHandler.post(() -> errorReporter.accept(error));
        }
    }
}

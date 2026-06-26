package com.mirkoddd.charon.internal.engine;

import com.mirkoddd.charon.catalog.CharonPlan;
import com.mirkoddd.charon.inventory.PurchaseStatus;
import com.mirkoddd.charon.catalog.SkuType;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import com.mirkoddd.charon.Charon;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.catalog.CharonPurchaseOption;
import com.mirkoddd.charon.checkout.BuyRequest;
import com.mirkoddd.charon.checkout.CheckoutFlow;
import com.mirkoddd.charon.checkout.RestoreCallback;
import com.mirkoddd.charon.checkout.SubscribeRequest;
import com.mirkoddd.charon.checkout.internal.PlayStoreCheckoutWorkflow;
import com.mirkoddd.charon.checkout.internal.PurchaseFulfiller;
import com.mirkoddd.charon.internal.billing.PurchaseBouncer;
import com.mirkoddd.charon.internal.billing.PurchaseMapper;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;
import com.mirkoddd.charon.inventory.InventoryCallback;
import com.mirkoddd.charon.observation.CharonCheckout;
import com.mirkoddd.charon.observation.CheckoutObservation;
import com.mirkoddd.charon.observation.StateObservation;
import com.mirkoddd.charon.observation.internal.InternalCheckoutObservation;
import com.mirkoddd.charon.observation.internal.InternalStateObservation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class CharonEngine extends Charon implements PurchasesUpdatedListener, InternalFlowOrchestrator {

    private final PurchaseStore purchaseStore;
    private final CatalogStore catalogStore;
    private final PurchaseFulfiller fulfiller;
    private final CharonLogger logger;
    private final IdentityManager identityManager;
    private final PurchaseRecoverer recoverer;
    private final FlowLauncher flowLauncher;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<CharonCheckout> checkoutLiveData = new MutableLiveData<>(new CharonCheckout.Idle());
    private final Map<String, CharonOffer.Mode> pendingModes = new ConcurrentHashMap<>();

    public CharonEngine(@NonNull PurchaseStore purchaseStore,
                        @NonNull CatalogStore catalogStore,
                        @NonNull IdentityManager identityManager,
                        @NonNull PurchaseRecoverer recoverer,
                        @NonNull FlowLauncher flowLauncher,
                        @NonNull PurchaseFulfiller fulfiller,
                        @NonNull CharonLogger logger) {
        this.logger = logger;
        this.purchaseStore = purchaseStore;
        this.catalogStore = catalogStore;
        this.identityManager = identityManager;
        this.recoverer = recoverer;
        this.flowLauncher = flowLauncher;
        this.fulfiller = fulfiller;

        this.flowLauncher.setErrorReporter(this::notifyError);

        refreshInventory();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static CharonSku createSubscription(String id, String groupId, int weight, List<String> entitlements,
                                               com.mirkoddd.charon.checkout.UpgradeMode upgradeMode) {
        return new InternalSku.Subscription(id, groupId, weight, entitlements, upgradeMode);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static CharonSku createConsumable(String id) {
        return new InternalSku.Consumable(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static CharonSku createNonConsumable(String id, List<String> entitlements) {
        return new InternalSku.NonConsumable(id, entitlements);
    }

    @Override
    public void setUserIdentity(@Nullable String accountId, @Nullable String profileId) {
        if (identityManager.update(accountId, profileId)) {
            logger.log("User identity updated: " + accountId + " / " + profileId);
            refreshInventory();
            refreshCatalog();
        }
    }

    private void refreshCatalog() {
        loadCatalog(true, true, new InternalFlowOrchestrator.CatalogCallbacks() {
            @Override
            public void onSuccess(@NonNull CharonCatalog catalog) {

            }

            @Override
            public void onError(@NonNull CharonError error) {
                logger.error("Failed to refresh catalog: " + error.message());
            }
        });
    }

    @NonNull
    @Override
    public StateObservation observeState() {
        return new InternalStateObservation(purchaseStore, catalogStore, owner -> {
            logger.log("State observer resumed, triggering sync...");
            refreshInventory();
            refreshCatalog();
        });
    }

    @NonNull
    @Override
    public CheckoutObservation observeCheckout() {
        return new InternalCheckoutObservation(this);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LiveData<CharonCheckout> getCheckoutLiveData() {
        return checkoutLiveData;
    }

    @Override
    public void forceRefresh() {
        logger.log("Manual refresh triggered.");
        refreshInventory();
        refreshCatalog();
    }

    private void refreshInventory() {
        recoverer.run(new PurchaseRecoverer.RecoveryListener() {
            @Override
            public void onPurchaseRecovered(@NonNull CharonPurchase cp) {
                logger.log("Purchase recovered successfully: " + cp.orderId() + ". Refreshing state...");

                mainHandler.postDelayed(() -> {
                    purchaseStore.refresh(identityManager.getCurrentSession().accountId(), 
                                        identityManager.getCurrentSession().profileId(), 
                                        new InventoryCallback() {
                        @Override public void onInventoryFetched(@NonNull CharonInventory inventory) {
                            // Inventory updated
                        }
                        @Override public void onFetchFailed(@NonNull CharonError error) {}
                    });
                }, 500);
                notifySuccess(cp);
            }

            @Override
            public void onRecoveryFailed(@NonNull CharonPurchase purchase, @NonNull CharonError error) {
                logger.log("Purchase recovery failed: " + purchase.orderId() + ". Refreshing state...");
                mainHandler.post(() -> {
                    purchaseStore.refresh(identityManager.getCurrentSession().accountId(), 
                                        identityManager.getCurrentSession().profileId(), 
                                        new InventoryCallback() {
                        @Override public void onInventoryFetched(@NonNull CharonInventory inventory) {
                            // Inventory updated
                        }
                        @Override public void onFetchFailed(@NonNull CharonError err) {}
                    });
                });
                notifyError(error);
            }
        });
    }

    @NonNull
    @Override
    public SubscribeRequest subscribe() {
        return plan -> new PlayStoreCheckoutWorkflow(this, plan);
    }

    @NonNull
    @Override
    public BuyRequest buy() {
        return offer -> new PlayStoreCheckoutWorkflow(this, offer);
    }

    @NonNull
    @Override
    public CheckoutFlow checkout(@NonNull CharonPurchaseOption option) {
        if (option instanceof CharonOffer offer) {
            return new PlayStoreCheckoutWorkflow(this, offer);
        } else if (option instanceof CharonPlan plan) {
            return new PlayStoreCheckoutWorkflow(this, plan);
        }
        throw new IllegalArgumentException("Unknown purchase option type: " + option.getClass().getName());
    }

    @Override
    public void restore(@NonNull RestoreCallback callback) {
        IdentitySession session = identityManager.getCurrentSession();
        purchaseStore.refresh(session.accountId(), session.profileId(), new InventoryCallback() {
            @Override
            public void onInventoryFetched(@NonNull CharonInventory inventory) {
                refreshCatalog();
                callback.onRestored(inventory);
            }

            @Override
            public void onFetchFailed(@NonNull CharonError error) {
                callback.onRestoreFailed(error);
            }
        });
    }

    @Override
    public void loadCatalog(boolean fetchSubs, boolean fetchInApp, @NonNull InternalFlowOrchestrator.CatalogCallbacks callbacks) {
        IdentitySession session = identityManager.getCurrentSession();
        catalogStore.fetchCatalog(fetchSubs, fetchInApp, session.accountId(), session.profileId(), callbacks);
    }

    @Override
    public void launchInAppFlow(@NonNull Activity activity, @NonNull CharonOffer offer) {
        if (handleRecoveryIntercept(offer.productId())) return;
        flowLauncher.launchInApp(activity, offer);
    }

    @Override
    public void launchSubscriptionFlow(@NonNull Activity activity, @NonNull CharonPlan plan) {
        if (handleRecoveryIntercept(plan.productId())) return;
        flowLauncher.launchSubscription(activity, plan);
    }

    private boolean handleRecoveryIntercept(String productId) {
        if (purchaseStore.getLastKnownInventory().statusFor(productId) == PurchaseStatus.VALIDATION_FAILED) {
            String targetToken = null;
            for (CharonPurchase cp : purchaseStore.getLastKnownInventory().networkErrorPurchases()) {
                if (cp.skus().contains(productId)) {
                    targetToken = cp.purchaseToken();
                    break;
                }
            }
            if (targetToken != null) {
                purchaseStore.clearNetworkError(targetToken);
            }
            notifyValidating(null); // No purchase yet, but we are validating
            recoverer.run(new PurchaseRecoverer.RecoveryListener() {
                @Override
                public void onPurchaseRecovered(@NonNull CharonPurchase cp) {
                    logger.log("Purchase recovered from intercept: " + cp.orderId());
                    mainHandler.postDelayed(() -> {
                        purchaseStore.refresh(identityManager.getCurrentSession().accountId(), 
                                            identityManager.getCurrentSession().profileId(), 
                                            new InventoryCallback() {
                            @Override public void onInventoryFetched(@NonNull CharonInventory inventory) {}
                            @Override public void onFetchFailed(@NonNull CharonError error) {}
                        });
                    }, 500);
                    notifySuccess(cp);
                }

                @Override
                public void onRecoveryFailed(@NonNull CharonPurchase purchase, @NonNull CharonError error) {
                    logger.log("Purchase recovery failed from intercept: " + purchase.orderId());
                    mainHandler.post(() -> {
                        purchaseStore.refresh(identityManager.getCurrentSession().accountId(), 
                                            identityManager.getCurrentSession().profileId(), 
                                            new InventoryCallback() {
                            @Override public void onInventoryFetched(@NonNull CharonInventory inventory) {}
                            @Override public void onFetchFailed(@NonNull CharonError err) {}
                        });
                    });
                    notifyError(error);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        int code = billingResult.getResponseCode();

        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchaseState(purchase);
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyCanceled();
        } else {
            CharonError error = new CharonError(code, billingResult.getDebugMessage());
            notifyError(error);
        }
    }

    @Override
    public void registerPendingOfferMode(@NonNull String productId, @NonNull com.mirkoddd.charon.catalog.CharonOffer.Mode mode) {
        pendingModes.put(productId, mode);
    }

    private void handlePurchaseState(Purchase purchase) {
        CharonSku skuConfig = fulfiller.getConfiguration().getSku(purchase.getProducts().get(0));
        boolean isSubscription = (skuConfig != null && skuConfig.type() == SkuType.SUBSCRIPTION);

        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            CharonPurchase charonPurchase = PurchaseMapper.map(purchase, isSubscription);

            if (!PurchaseBouncer.isSignatureValid(charonPurchase, fulfiller.getConfiguration())) {
                CharonError error = new CharonError(BillingClient.BillingResponseCode.ERROR, "Local cryptographic signature validation failed");
                notifyError(error);
                return;
            }

            purchaseStore.markAsValidating(charonPurchase);

            if (fulfiller.hasInterceptor()) {
                notifyValidating(charonPurchase);
            }

            com.mirkoddd.charon.catalog.CharonOffer.Mode mode = pendingModes.remove(purchase.getProducts().get(0));

            fulfiller.fulfill(purchase, mode, new PurchaseFulfiller.FulfillmentCallback() {
                @Override
                public void onSuccess(CharonPurchase fulfilledPurchase) {
                    purchaseStore.clearValidating(purchase.getPurchaseToken());
                    mainHandler.postDelayed(() -> {
                        refreshInventory();
                        refreshCatalog();
                    }, 500);
                    notifySuccess(fulfilledPurchase);
                }

                @Override
                public void onFailed(int code, String message) {
                    purchaseStore.clearValidating(purchase.getPurchaseToken());
                    if (fulfiller.hasInterceptor()) {
                        purchaseStore.markAsRejected(purchase.getPurchaseToken());
                        refreshInventory();
                    }
                    CharonError error = new CharonError(code, message);
                    notifyError(error);
                }

                @Override
                public void onNetworkError(int code, String message) {
                    purchaseStore.clearValidating(purchase.getPurchaseToken());
                    if (fulfiller.hasInterceptor()) {
                        purchaseStore.markAsNetworkError(purchase.getPurchaseToken());
                        refreshInventory();
                    }
                    CharonError error = new CharonError(code, message);
                    notifyError(error);
                }
            });
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            refreshInventory();
            CharonPurchase cp = PurchaseMapper.map(purchase, isSubscription);
            notifyPending(cp);
        }
    }

    private void notifySuccess(CharonPurchase p) {
        checkoutLiveData.postValue(new CharonCheckout.Success(p));
    }

    private void notifyPending(CharonPurchase p) {
        checkoutLiveData.postValue(new CharonCheckout.Pending(p));
    }

    private void notifyValidating(@Nullable CharonPurchase p) {
        checkoutLiveData.postValue(new CharonCheckout.Validating(p));
    }

    private void notifyError(CharonError e) {
        checkoutLiveData.postValue(new CharonCheckout.Failed(e));
    }

    private void notifyCanceled() {
        checkoutLiveData.postValue(new CharonCheckout.UserCanceled());
    }
}

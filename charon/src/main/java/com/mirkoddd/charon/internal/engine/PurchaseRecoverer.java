package com.mirkoddd.charon.internal.engine;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.Purchase;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.checkout.internal.PurchaseFulfiller;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;
import com.mirkoddd.charon.inventory.InventoryCallback;

import org.json.JSONException;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PurchaseRecoverer {

    private final PurchaseStore purchaseStore;
    private final PurchaseFulfiller fulfiller;
    private final IdentityManager identityManager;
    private final CharonLogger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RecoveryListener {
        void onPurchaseRecovered(@NonNull CharonPurchase purchase);
        void onRecoveryFailed(@NonNull CharonPurchase purchase, @NonNull CharonError error);
    }

    public PurchaseRecoverer(@NonNull PurchaseStore purchaseStore,
                            @NonNull PurchaseFulfiller fulfiller,
                            @NonNull IdentityManager identityManager,
                            @NonNull CharonLogger logger) {
        this.purchaseStore = purchaseStore;
        this.fulfiller = fulfiller;
        this.identityManager = identityManager;
        this.logger = logger;
    }

    public void run(@NonNull RecoveryListener listener) {
        IdentitySession session = identityManager.getCurrentSession();
        purchaseStore.refresh(session.accountId(), session.profileId(), new InventoryCallback() {
            @Override
            public void onInventoryFetched(@NonNull CharonInventory inventory) {
                logger.log("Inventory refresh completed for recovery.");
                for (CharonPurchase unfulfilled : inventory.unfulfilledPurchases()) {
                    recover(unfulfilled, listener);
                }
            }

            @Override
            public void onFetchFailed(@NonNull CharonError error) {
                logger.error("Silent inventory refresh failed during recovery: " + error.message());
            }
        });
    }

    private void recover(CharonPurchase unfulfilled, RecoveryListener listener) {
        if (!isAuthorized(unfulfilled)) {
            logger.error("Bouncer: Unauthorized purchase found! Session: " + identityManager.getCurrentSession().accountId() + " Receipt: " + unfulfilled.accountId());
            return;
        }

        logger.log("Recovering unfulfilled purchase: " + unfulfilled.orderId());
        try {
            Purchase nativePurchase = new Purchase(unfulfilled.originalJson(), unfulfilled.signature());
            fulfiller.fulfill(nativePurchase, null, new PurchaseFulfiller.FulfillmentCallback() {
                @Override
                public void onSuccess(CharonPurchase cp) {
                    mainHandler.post(() -> listener.onPurchaseRecovered(cp));
                }

                @Override
                public void onFailed(int code, String message) {
                    if (fulfiller.hasInterceptor()) {
                        purchaseStore.markAsRejected(nativePurchase.getPurchaseToken());
                    }
                    logger.error("Failed to recover purchase: " + message);
                    mainHandler.post(() -> listener.onRecoveryFailed(unfulfilled, new CharonError(code, message)));
                }

                @Override
                public void onNetworkError(int code, String message) {
                    if (fulfiller.hasInterceptor()) {
                        purchaseStore.markAsNetworkError(nativePurchase.getPurchaseToken());
                    }
                    logger.error("Network error recovering purchase: " + message);
                    mainHandler.post(() -> listener.onRecoveryFailed(unfulfilled, new CharonError(code, message)));
                }
            });
        } catch (JSONException e) {
            logger.error("JSON Error parsing unfulfilled purchase: " + e.getMessage());
        }
    }

    private boolean isAuthorized(CharonPurchase purchase) {
        String receiptAccountId = purchase.accountId();
        String sessionAccountId = identityManager.getCurrentSession().accountId();

        if (receiptAccountId == null || receiptAccountId.isEmpty()) {
            return true;
        }

        return receiptAccountId.equals(sessionAccountId);
    }
}

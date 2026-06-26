package com.mirkoddd.charon.checkout.internal;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.catalog.SkuType;
import com.mirkoddd.charon.CharonFulfillment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.Purchase;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.internal.engine.CharonLogger;
import com.mirkoddd.charon.inventory.CharonPurchase;
import com.mirkoddd.charon.internal.billing.PurchaseMapper;

import java.util.HashSet;
import java.util.Set;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PurchaseFulfiller {

    private final BillingClient billingClient;
    private final CharonConfiguration configuration;
    private final CharonLogger logger;
    private final Set<String> processingTokens = new HashSet<>();

    public interface FulfillmentCallback {
        void onSuccess(CharonPurchase purchase);
        void onFailed(int code, String message);
        void onNetworkError(int code, String message);
    }

    public PurchaseFulfiller(
            @NonNull BillingClient billingClient,
            @NonNull CharonConfiguration configuration,
            @NonNull CharonLogger logger) {
        this.billingClient = billingClient;
        this.configuration = configuration;
        this.logger = logger;
    }

    @NonNull
    public CharonConfiguration getConfiguration() {
        return configuration;
    }

    public boolean hasInterceptor() {
        return configuration.getInterceptor() != null;
    }

    public void fulfill(@NonNull Purchase purchase, @Nullable CharonOffer.Mode mode, @NonNull FulfillmentCallback callback) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            callback.onFailed(-1, "Purchase is not in PURCHASED state");
            return;
        }

        String token = purchase.getPurchaseToken();
        synchronized (processingTokens) {
            if (processingTokens.contains(token)) {
                logger.log("Fulfillment already in progress for token, ignoring duplicate request.");
                return;
            }
            processingTokens.add(token);
        }

        var skuId = purchase.getProducts().get(0);
        var skuConfig = configuration.getSku(skuId);
        boolean isSubscription = (skuConfig != null && skuConfig.type() == SkuType.SUBSCRIPTION);

        FulfillmentCallback wrappedCallback = new FulfillmentCallback() {
            @Override
            public void onSuccess(CharonPurchase purchase) {
                synchronized (processingTokens) {
                    processingTokens.remove(token);
                }
                callback.onSuccess(purchase);
            }

            @Override
            public void onFailed(int code, String message) {
                synchronized (processingTokens) {
                    processingTokens.remove(token);
                }
                callback.onFailed(code, message);
            }

            @Override
            public void onNetworkError(int code, String message) {
                synchronized (processingTokens) {
                    processingTokens.remove(token);
                }
                callback.onNetworkError(code, message);
            }
        };

        CharonPurchase charonPurchase = PurchaseMapper.map(purchase, isSubscription);
        var interceptor = configuration.getInterceptor();

        if (interceptor != null) {
            ExecutorService executor = Executors.newCachedThreadPool();
            Future<CharonFulfillment> future = executor.submit(() -> interceptor.onValidatePurchase(charonPurchase));

            executor.execute(() -> {
                try {
                    CharonFulfillment result = future.get(configuration.getFulfillmentTimeoutSeconds(), TimeUnit.SECONDS);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (result == CharonFulfillment.ACCEPTED) {
                            processFulfillment(purchase, skuConfig, mode, isSubscription, wrappedCallback);
                        } else if (result == CharonFulfillment.REJECTED) {
                            wrappedCallback.onFailed(403, "Purchase rejected by backend");
                        } else {
                            wrappedCallback.onNetworkError(500, "Server unavailable or validation failed");
                        }
                    });
                } catch (TimeoutException e) {
                    future.cancel(true);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        wrappedCallback.onNetworkError(408, "Validation timeout exceeded");
                    });
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        wrappedCallback.onNetworkError(500, "Validation exception: " + e.getMessage());
                    });
                } finally {
                    executor.shutdown();
                }
            });
        } else {
            processFulfillment(purchase, skuConfig, mode, isSubscription, wrappedCallback);
        }
    }

    private void processFulfillment(Purchase purchase, CharonSku skuConfig, @Nullable com.mirkoddd.charon.catalog.CharonOffer.Mode mode, boolean isSubscription, FulfillmentCallback callback) {
        boolean shouldConsume = (skuConfig != null && skuConfig.type() == SkuType.CONSUMABLE);

        if (shouldConsume) {
            consume(purchase, isSubscription, callback);
        } else {
            acknowledge(purchase, isSubscription, callback);
        }
    }

    private void consume(Purchase purchase, boolean isSubscription, FulfillmentCallback callback) {
        var params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.consumeAsync(params, (result, token) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                callback.onSuccess(PurchaseMapper.map(purchase, isSubscription));
            } else {
                callback.onFailed(result.getResponseCode(), result.getDebugMessage());
            }
        });
    }

    private void acknowledge(Purchase purchase, boolean isSubscription, FulfillmentCallback callback) {
        if (purchase.isAcknowledged()) {
            callback.onSuccess(PurchaseMapper.map(purchase, isSubscription));
            return;
        }

        var params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, result -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                callback.onSuccess(PurchaseMapper.map(purchase, isSubscription));
            } else {
                callback.onFailed(result.getResponseCode(), result.getDebugMessage());
            }
        });
    }
}

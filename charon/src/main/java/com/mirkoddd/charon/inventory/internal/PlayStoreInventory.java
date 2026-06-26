package com.mirkoddd.charon.inventory.internal;

import android.os.Handler;
import android.os.Looper;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.QueryPurchasesParams;
import com.mirkoddd.charon.internal.billing.InventoryMapper;
import com.mirkoddd.charon.internal.engine.BillingBridge;
import com.mirkoddd.charon.internal.engine.CharonLogger;
import com.mirkoddd.charon.internal.engine.PurchaseStore;
import com.mirkoddd.charon.inventory.InventoryCallback;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PlayStoreInventory implements PurchaseStore {

    private final BillingBridge billingBridge;
    private final CharonConfiguration configuration;
    private final CharonLogger logger;
    private final MutableLiveData<CharonInventory> inventoryLiveData = new MutableLiveData<>();
    private final AtomicLong lastRequestId = new AtomicLong(0);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<Consumer<CharonInventory>> pendingInventoryTasks = new ArrayList<>();
    private final AtomicLong activeRefreshes = new AtomicLong(0);
    private final Set<String> rejectedTokens = new CopyOnWriteArraySet<>();
    private final Set<String> networkErrorTokens = new CopyOnWriteArraySet<>();
    private final Set<CharonPurchase> validatingPurchases = new CopyOnWriteArraySet<>();
    private boolean isInitialized = false;

    private volatile CharonInventory cachedInventory = CharonInventory.empty();

    public PlayStoreInventory(@NonNull BillingBridge billingBridge, @NonNull CharonConfiguration configuration, @NonNull CharonLogger logger) {
        this.billingBridge = billingBridge;
        this.configuration = configuration;
        this.logger = logger;
    }

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<CharonInventory> observer) {
        inventoryLiveData.observe(owner, observer);
    }

    @NonNull
    @Override
    public LiveData<CharonInventory> getLiveData() {
        return inventoryLiveData;
    }

    private Runnable pendingEmptyUpdate = null;

    @Override
    public void markAsRejected(@NonNull String purchaseToken) {
        rejectedTokens.add(purchaseToken);
        networkErrorTokens.remove(purchaseToken);
    }

    @Override
    public void markAsNetworkError(@NonNull String purchaseToken) {
        networkErrorTokens.add(purchaseToken);
        rejectedTokens.remove(purchaseToken);
    }

    @Override
    public void clearNetworkError(@NonNull String purchaseToken) {
        networkErrorTokens.remove(purchaseToken);
    }

    @Override
    public void markAsValidating(@NonNull CharonPurchase purchase) {
        validatingPurchases.add(purchase);
    }

    @Override
    public void clearValidating(@NonNull String purchaseToken) {
        validatingPurchases.removeIf(p -> p.purchaseToken().equals(purchaseToken));
    }

    @Override
    public void updateInventory(@NonNull CharonInventory inventory) {
        if (!isInitialized) {
            this.cachedInventory = inventory;
            inventoryLiveData.postValue(inventory);
            notifyPendingTasks(inventory);
            return;
        }

        if (inventory.activeSkus().isEmpty() && !cachedInventory.activeSkus().isEmpty()) {
            logger.log("Inventory is suspiciously empty. Delaying update to avoid UI flickering...");
            if (pendingEmptyUpdate != null) {
                mainHandler.removeCallbacks(pendingEmptyUpdate);
            }
            pendingEmptyUpdate = () -> {
                logger.log("Applying delayed empty inventory.");
                this.cachedInventory = inventory;
                inventoryLiveData.postValue(inventory);
                pendingEmptyUpdate = null;
                notifyPendingTasks(inventory);
            };
            mainHandler.postDelayed(pendingEmptyUpdate, 2000);
        } else {
            if (pendingEmptyUpdate != null) {
                mainHandler.removeCallbacks(pendingEmptyUpdate);
                pendingEmptyUpdate = null;
            }
            this.cachedInventory = inventory;
            inventoryLiveData.postValue(inventory);
            notifyPendingTasks(inventory);
        }
    }

    private void notifyPendingTasks(CharonInventory inventory) {
        List<Consumer<CharonInventory>> tasks;
        synchronized (pendingInventoryTasks) {
            isInitialized = true;
            tasks = new ArrayList<>(pendingInventoryTasks);
            pendingInventoryTasks.clear();
        }
        for (var task : tasks) {
            task.accept(inventory);
        }
    }

    @Override
    public void executeWhenInventoryReady(@NonNull Consumer<CharonInventory> action) {
        boolean shouldRunNow = false;
        synchronized (pendingInventoryTasks) {
            if (activeRefreshes.get() > 0 || !isInitialized) {
                pendingInventoryTasks.add(action);
            } else {
                shouldRunNow = true;
            }
        }
        if (shouldRunNow) {
            action.accept(getLastKnownInventory());
        }
    }

    @Override
    public void refresh(@Nullable String accountId, @Nullable String profileId, @NonNull InventoryCallback callback) {
        long requestId = lastRequestId.incrementAndGet();
        activeRefreshes.incrementAndGet();
        billingBridge.executeWhenReady(client -> fetchFromGoogle(client, accountId, profileId, requestId, callback), error -> {
            activeRefreshes.decrementAndGet();
            notifyPendingTasks(cachedInventory);
            if (requestId == lastRequestId.get()) {
                callback.onFetchFailed(error);
            }
        });
    }

    private void fetchFromGoogle(BillingClient client, @Nullable String accountId, @Nullable String profileId, long requestId, InventoryCallback callback) {
        if (requestId < lastRequestId.get()) {
            activeRefreshes.decrementAndGet();
            return;
        }

        QueryPurchasesParams subParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        client.queryPurchasesAsync(subParams, (subResult, subPurchases) -> {
            if (requestId < lastRequestId.get()) {
                activeRefreshes.decrementAndGet();
                return;
            }

            if (subResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                activeRefreshes.decrementAndGet();
                notifyPendingTasks(cachedInventory);
                callback.onFetchFailed(new CharonError(subResult.getResponseCode(), subResult.getDebugMessage()));
                return;
            }

            QueryPurchasesParams inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();

            client.queryPurchasesAsync(inAppParams, (inAppResult, inAppPurchases) -> {
                if (requestId < lastRequestId.get()) {
                    activeRefreshes.decrementAndGet();
                    return;
                }

                if (inAppResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    activeRefreshes.decrementAndGet();
                    notifyPendingTasks(cachedInventory);
                    callback.onFetchFailed(new CharonError(inAppResult.getResponseCode(), inAppResult.getDebugMessage()));
                    return;
                }

                if (subResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    logger.log("Google Play: Found " + subPurchases.size() + " active subscriptions.");
                }

                CharonInventory inventory = InventoryMapper.map(
                        subPurchases,
                        inAppPurchases,
                        rejectedTokens,
                        networkErrorTokens,
                        validatingPurchases,
                        configuration,
                        accountId
                );

                logger.log("Charon Inventory Mapped: Active=" + inventory.activeSkus().size());

                activeRefreshes.decrementAndGet();
                updateInventory(inventory);
                callback.onInventoryFetched(inventory);
            });
        });
    }

    @NonNull
    @Override
    public CharonInventory getLastKnownInventory() {
        return cachedInventory;
    }
}

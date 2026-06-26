package com.mirkoddd.charon.catalog.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.mirkoddd.charon.CharonConfiguration;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.catalog.CharonInApp;
import com.mirkoddd.charon.catalog.CharonSubscription;
import com.mirkoddd.charon.internal.billing.CatalogMapper;
import com.mirkoddd.charon.internal.engine.BillingBridge;
import com.mirkoddd.charon.internal.engine.CatalogStore;
import com.mirkoddd.charon.internal.engine.CharonLogger;
import com.mirkoddd.charon.internal.engine.InternalFlowOrchestrator;
import com.mirkoddd.charon.internal.engine.PurchaseStore;
import com.mirkoddd.charon.inventory.CharonInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PlayStoreCatalog implements CatalogStore {

    private final BillingBridge billingBridge;
    private final PurchaseStore purchaseStore;
    private final CharonConfiguration configuration;
    private final CharonLogger logger;
    private final MutableLiveData<CharonCatalog> catalogLiveData = new MutableLiveData<>();
    private final List<InternalFlowOrchestrator.CatalogCallbacks> pendingCallbacks = new ArrayList<>();
    private final AtomicBoolean isFetching = new AtomicBoolean(false);
    private volatile CharonCatalog cachedCatalog = null;
    
    public PlayStoreCatalog(@NonNull BillingBridge billingBridge, @NonNull PurchaseStore purchaseStore, @NonNull CharonConfiguration configuration, @NonNull CharonLogger logger) {
        this.billingBridge = billingBridge;
        this.purchaseStore = purchaseStore;
        this.configuration = configuration;
        this.logger = logger;
    }

    @Override
    public void observeCatalog(@NonNull LifecycleOwner owner, @NonNull Observer<CharonCatalog> observer) {
        catalogLiveData.observe(owner, observer);
    }

    @NonNull
    @Override
    public LiveData<CharonCatalog> getLiveData() {
        return catalogLiveData;
    }

    @Override
    public void fetchCatalog(boolean fetchSubs, boolean fetchInApp, @Nullable String accountId, @Nullable String profileId, @NonNull InternalFlowOrchestrator.CatalogCallbacks callbacks) {
        synchronized (pendingCallbacks) {
            pendingCallbacks.add(callbacks);
            if (isFetching.getAndSet(true)) {
                return;
            }
        }

        billingBridge.executeWhenReady(client -> {
            final List<CharonSubscription> subs = Collections.synchronizedList(new ArrayList<>());
            final List<CharonInApp> items = Collections.synchronizedList(new ArrayList<>());
            final int totalRequests = (fetchSubs ? 1 : 0) + (fetchInApp ? 1 : 0);
            
            if (totalRequests == 0) {
                onFetchFinished(new CharonCatalog(Collections.emptyList(), Collections.emptyList()), null);
                return;
            }

            final int[] completed = {0};
            final CharonError[] firstError = {null};

            if (fetchSubs) {
                querySubs(client, (error, list) -> {
                    if (error != null) firstError[0] = error;
                    else subs.addAll(list);
                    checkAllDone(++completed[0], totalRequests, subs, items, firstError[0]);
                });
            }

            if (fetchInApp) {
                queryInApp(client, (error, list) -> {
                    if (error != null) firstError[0] = error;
                    else items.addAll(list);
                    checkAllDone(++completed[0], totalRequests, subs, items, firstError[0]);
                });
            }
        }, error -> onFetchFinished(null, error));
    }

    private void checkAllDone(int done, int total, List<CharonSubscription> subs, List<CharonInApp> items, CharonError error) {
        if (done == total) {
            if (error != null && subs.isEmpty() && items.isEmpty()) {
                onFetchFinished(null, error);
            } else {
                onFetchFinished(new CharonCatalog(new ArrayList<>(subs), new ArrayList<>(items)), null);
            }
        }
    }

    private void onFetchFinished(@Nullable CharonCatalog catalog, @Nullable CharonError error) {
        isFetching.set(false);
        List<InternalFlowOrchestrator.CatalogCallbacks> callbacks;
        synchronized (pendingCallbacks) {
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        if (catalog != null) {
            cachedCatalog = catalog;
            catalogLiveData.postValue(catalog);
            for (var cb : callbacks) cb.onSuccess(catalog);
        } else if (error != null) {
            for (var cb : callbacks) cb.onError(error);
        }
    }

    @Nullable
    @Override
    public CharonCatalog getLastKnownCatalog() {
        return cachedCatalog;
    }

    private void querySubs(BillingClient client, CatalogInternalCallback<CharonSubscription> cb) {
        List<String> ids = configuration.getSubscriptionIds();
        if (ids.isEmpty()) {
            cb.onResult(null, Collections.emptyList());
            return;
        }

        client.queryProductDetailsAsync(buildQueryParams(ids, BillingClient.ProductType.SUBS), (result, prodResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                cb.onResult(new CharonError(result.getResponseCode(), result.getDebugMessage()), Collections.emptyList());
                return;
            }

            for (var unfetched : prodResult.getUnfetchedProductList()) {
                logger.error("Failed to fetch product " + unfetched.getProductId() + ": " + unfetched.getStatusCode());
            }

            List<ProductDetails> details = prodResult.getProductDetailsList();
            purchaseStore.executeWhenInventoryReady(inventory -> {
                List<CharonSubscription> mapped = new ArrayList<>();
                for (ProductDetails pd : details) {
                    CharonSku skuConfig = configuration.getSku(pd.getProductId());
                    if (skuConfig != null) {
                        mapped.add(CatalogMapper.mapSubscription(pd, skuConfig, isInGroup(inventory, skuConfig)));
                    }
                }
                cb.onResult(null, mapped);
            });
        });
    }

    private void queryInApp(BillingClient client, CatalogInternalCallback<CharonInApp> cb) {
        List<String> ids = configuration.getInAppIds();
        if (ids.isEmpty()) {
            cb.onResult(null, Collections.emptyList());
            return;
        }

        client.queryProductDetailsAsync(buildQueryParams(ids, BillingClient.ProductType.INAPP), (result, prodResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                cb.onResult(new CharonError(result.getResponseCode(), result.getDebugMessage()), Collections.emptyList());
                return;
            }

            for (var unfetched : prodResult.getUnfetchedProductList()) {
                logger.error("Failed to fetch product " + unfetched.getProductId() + ": " + unfetched.getStatusCode());
            }

            List<ProductDetails> details = prodResult.getProductDetailsList();
            purchaseStore.executeWhenInventoryReady(inventory -> {
                List<CharonInApp> mapped = new ArrayList<>();
                for (ProductDetails pd : details) {
                    CharonSku skuConfig = configuration.getSku(pd.getProductId());
                    if (skuConfig != null) {
                        try {
                            mapped.add(CatalogMapper.mapInApp(pd, skuConfig, logger));
                        } catch (Exception e) {
                            logger.error("Failed to map in-app product " + pd.getProductId() + ": " + e.getMessage());
                        }
                    }
                }
                cb.onResult(null, mapped);
            });
        });
    }

    private boolean isInGroup(CharonInventory inventory, CharonSku skuConfig) {
        if (skuConfig.groupId() == null) return false;
        for (String ownedSkuId : inventory.activeSkus()) {
            var ownedSku = configuration.getSku(ownedSkuId);
            if (ownedSku != null && Objects.equals(skuConfig.groupId(), ownedSku.groupId())) return true;
        }
        return false;
    }

    private QueryProductDetailsParams buildQueryParams(List<String> ids, String type) {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String id : ids) {
            productList.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build());
        }
        return QueryProductDetailsParams.newBuilder().setProductList(productList).build();
    }

    private interface CatalogInternalCallback<T> {
        void onResult(@Nullable CharonError error, @NonNull List<T> list);
    }
}


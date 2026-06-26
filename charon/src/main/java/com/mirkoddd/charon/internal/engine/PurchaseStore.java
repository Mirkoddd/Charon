package com.mirkoddd.charon.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;
import com.mirkoddd.charon.inventory.InventoryCallback;

import java.util.function.Consumer;

import androidx.lifecycle.LiveData;

import com.mirkoddd.charon.catalog.CharonCatalog;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PurchaseStore {

    void observe(@NonNull LifecycleOwner owner, @NonNull Observer<CharonInventory> observer);

    @NonNull
    LiveData<CharonInventory> getLiveData();

    void refresh(@Nullable String accountId, @Nullable String profileId, @NonNull InventoryCallback callback);

    @NonNull
    CharonInventory getLastKnownInventory();

    void updateInventory(@NonNull CharonInventory inventory);

    void markAsRejected(@NonNull String purchaseToken);
    
    void markAsNetworkError(@NonNull String purchaseToken);

    void clearNetworkError(@NonNull String purchaseToken);

    void markAsValidating(@NonNull CharonPurchase purchase);

    void clearValidating(@NonNull String purchaseToken);

    void executeWhenInventoryReady(@NonNull Consumer<CharonInventory> action);
}

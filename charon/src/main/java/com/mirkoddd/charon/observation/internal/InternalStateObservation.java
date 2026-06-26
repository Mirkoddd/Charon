package com.mirkoddd.charon.observation.internal;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.internal.engine.PurchaseStore;
import com.mirkoddd.charon.internal.engine.CatalogStore;
import com.mirkoddd.charon.observation.CharonState;
import com.mirkoddd.charon.observation.StateObservation;
import java.util.function.Consumer;

public final class InternalStateObservation implements StateObservation {

    private final PurchaseStore purchaseStore;
    private final CatalogStore catalogStore;
    private final Consumer<LifecycleOwner> onResumeAction;

    private CharonInventory latestInventory;
    private CharonCatalog latestCatalog;

    public InternalStateObservation(@NonNull PurchaseStore purchaseStore, @NonNull CatalogStore catalogStore, @NonNull Consumer<LifecycleOwner> onResumeAction) {
        this.purchaseStore = purchaseStore;
        this.catalogStore = catalogStore;
        this.onResumeAction = onResumeAction;
    }

    @NonNull
    @Override
    public LiveData<CharonState> connectionState() {
        MediatorLiveData<CharonState> mediator = new AutoSyncLiveData<>() {
            @Override
            protected void onActive() {
                super.onActive();
                onResumeAction.accept(null);
            }
        };
        mediator.addSource(purchaseStore.getLiveData(), inventory -> {
            latestInventory = inventory;
            updateSyncMediator(mediator);
        });
        mediator.addSource(catalogStore.getLiveData(), catalog -> {
            latestCatalog = catalog;
            updateSyncMediator(mediator);
        });
        return mediator;
    }

    private void updateSyncMediator(MediatorLiveData<CharonState> mediator) {
        if (latestInventory != null && latestCatalog != null) {
            mediator.setValue(new CharonState(latestInventory, latestCatalog));
        }
    }

    @NonNull
    @Override
    public LiveData<CharonInventory> inventory() {
        return wrapWithAutoSync(purchaseStore.getLiveData());
    }

    @NonNull
    @Override
    public LiveData<CharonCatalog> catalog() {
        return wrapWithAutoSync(catalogStore.getLiveData());
    }

    private <T> LiveData<T> wrapWithAutoSync(LiveData<T> source) {
        MediatorLiveData<T> mediator = new MediatorLiveData<>() {
            @Override
            protected void onActive() {
                super.onActive();
                onResumeAction.accept(null);
            }
        };
        mediator.addSource(source, mediator::setValue);
        return mediator;
    }

    private static class AutoSyncLiveData<T> extends MediatorLiveData<T> {
    }
}

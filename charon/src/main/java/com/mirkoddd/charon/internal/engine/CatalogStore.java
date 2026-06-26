package com.mirkoddd.charon.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.mirkoddd.charon.catalog.CharonCatalog;

import androidx.lifecycle.LiveData;
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CatalogStore {
    void observeCatalog(@NonNull LifecycleOwner owner, @NonNull Observer<CharonCatalog> observer);

    @NonNull
    LiveData<CharonCatalog> getLiveData();
    void fetchCatalog(boolean fetchSubs, boolean fetchInApp, @Nullable String accountId, @Nullable String profileId, @NonNull InternalFlowOrchestrator.CatalogCallbacks callbacks);

    @Nullable
    CharonCatalog getLastKnownCatalog();
}

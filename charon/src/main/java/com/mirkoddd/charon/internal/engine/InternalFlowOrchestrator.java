package com.mirkoddd.charon.internal.engine;

import com.mirkoddd.charon.catalog.CharonPlan;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.CharonError;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface InternalFlowOrchestrator {

    void launchInAppFlow(@NonNull Activity activity, @NonNull CharonOffer offer);

    void launchSubscriptionFlow(@NonNull Activity activity, @NonNull CharonPlan plan);

    void loadCatalog(boolean fetchSubs, boolean fetchInApp, @NonNull CatalogCallbacks callbacks);

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void registerPendingOfferMode(@NonNull String purchaseToken, @NonNull CharonOffer.Mode mode);

    public interface CatalogCallbacks {
        void onSuccess(@NonNull CharonCatalog catalog);
        void onError(@NonNull CharonError error);
    }
}

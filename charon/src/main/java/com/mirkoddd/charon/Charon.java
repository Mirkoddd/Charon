package com.mirkoddd.charon;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mirkoddd.charon.catalog.CharonPurchaseOption;
import com.mirkoddd.charon.checkout.CheckoutFlow;
import com.mirkoddd.charon.checkout.BuyRequest;
import com.mirkoddd.charon.checkout.RestoreCallback;
import com.mirkoddd.charon.checkout.SubscribeRequest;
import com.mirkoddd.charon.observation.CheckoutObservation;
import com.mirkoddd.charon.observation.StateObservation;

public abstract class Charon {
    private static Charon instance;

    @NonNull
    public static CharonBuilder.SkuConfigurator init(@NonNull Application application) {
        return CharonBuilder.create(application);
    }

    @NonNull
    public static Charon getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Charon is not initialized. Call Charon.init() first.");
        }
        return instance;
    }

    static void setInstance(Charon charonInstance) {
        instance = charonInstance;
    }

    public abstract void setUserIdentity(@Nullable String accountId, @Nullable String profileId);

    public void setUserIdentity(@NonNull String accountId) {
        setUserIdentity(accountId, null);
    }

    public void clearUserIdentity() {
        setUserIdentity(null, null);
    }

    public abstract void forceRefresh();

    @NonNull public abstract SubscribeRequest subscribe();
    @NonNull public abstract BuyRequest buy();
    @NonNull public abstract CheckoutFlow checkout(@NonNull CharonPurchaseOption option);
    public abstract void restore(@NonNull RestoreCallback callback);

    @NonNull
    public abstract StateObservation observeState();

    @NonNull
    public abstract CheckoutObservation observeCheckout();
}

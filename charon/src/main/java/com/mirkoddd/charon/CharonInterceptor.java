package com.mirkoddd.charon;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.mirkoddd.charon.inventory.CharonPurchase;

@FunctionalInterface
public interface CharonInterceptor {

    @NonNull
    @WorkerThread
    CharonFulfillment onValidatePurchase(@NonNull CharonPurchase purchase);
}

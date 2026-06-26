package com.mirkoddd.charon.checkout;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.catalog.CharonPlan;

public interface SubscribeRequest {
    @NonNull CheckoutFlow to(@NonNull CharonPlan plan);
}

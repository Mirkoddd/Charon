package com.mirkoddd.charon.observation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

public interface CheckoutObservation {
    @NonNull LiveData<CharonCheckout> events();
}

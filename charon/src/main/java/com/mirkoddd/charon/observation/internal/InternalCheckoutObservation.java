package com.mirkoddd.charon.observation.internal;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.mirkoddd.charon.internal.engine.CharonEngine;
import com.mirkoddd.charon.observation.CharonCheckout;
import com.mirkoddd.charon.observation.CheckoutObservation;

public final class InternalCheckoutObservation implements CheckoutObservation {

    private final CharonEngine engine;

    public InternalCheckoutObservation(@NonNull CharonEngine engine) {
        this.engine = engine;
    }

    @NonNull
    @Override
    public LiveData<CharonCheckout> events() {
        return engine.getCheckoutLiveData();
    }
}

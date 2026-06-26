package com.mirkoddd.charon.checkout;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.catalog.CharonOffer;

public interface BuyRequest {
    @NonNull CheckoutFlow item(@NonNull CharonOffer offer);
}

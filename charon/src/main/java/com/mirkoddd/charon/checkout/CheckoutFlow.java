package com.mirkoddd.charon.checkout;

import android.app.Activity;
import androidx.annotation.NonNull;

public interface CheckoutFlow {
    void launch(@NonNull Activity activity);
}

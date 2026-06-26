package com.mirkoddd.charon.sample.config;

import androidx.annotation.NonNull;

import com.mirkoddd.charon.CharonFulfillment;
import com.mirkoddd.charon.CharonInterceptor;
import com.mirkoddd.charon.inventory.CharonPurchase;

/**
 * Created by Mirko Dimartino on 13/04/26.
 */
public class FakeDelayInterceptor implements CharonInterceptor {
    @NonNull
    @Override
    public CharonFulfillment onValidatePurchase(@NonNull CharonPurchase purchase) {
        try {
            Thread.sleep(2000); // here you should validate the purchase in the server, for demo purposes there is a simple delay
        } catch (InterruptedException ignore) {}

        return CharonFulfillment.ACCEPTED;
    }
}

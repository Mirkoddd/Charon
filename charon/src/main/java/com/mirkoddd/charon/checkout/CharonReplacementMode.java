package com.mirkoddd.charon.checkout;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum CharonReplacementMode {

    WITH_TIME_PRORATION,

    DEFERRED,

    CHARGE_FULL_PRICE,

    CHARGE_PRORATED_PRICE,

    WITHOUT_PRORATION
}
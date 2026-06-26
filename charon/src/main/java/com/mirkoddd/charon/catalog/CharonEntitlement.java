package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;

/**
 * Interface to be implemented by an Enum to define the application's entitlements (benefits).
 * This allows type-safe mapping between products and the features they unlock.
 */
public interface CharonEntitlement {
    @NonNull String id();
}

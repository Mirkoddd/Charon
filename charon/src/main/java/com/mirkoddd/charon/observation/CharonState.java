package com.mirkoddd.charon.observation;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.inventory.CharonInventory;

public record CharonState(
        @NonNull CharonInventory inventory,
        @NonNull CharonCatalog catalog) {
}

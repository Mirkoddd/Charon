package com.mirkoddd.charon.observation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.inventory.CharonInventory;

public interface StateObservation {
    @NonNull LiveData<CharonState> connectionState();
    @NonNull LiveData<CharonInventory> inventory();
    @NonNull LiveData<CharonCatalog> catalog();
}

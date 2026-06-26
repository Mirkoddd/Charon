package com.mirkoddd.charon.inventory;

import androidx.annotation.NonNull;

import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.inventory.CharonInventory;

public interface InventoryCallback {

    void onInventoryFetched(@NonNull CharonInventory inventory);

    void onFetchFailed(@NonNull CharonError error);
}
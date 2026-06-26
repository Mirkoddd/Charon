package com.mirkoddd.charon.checkout;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.inventory.CharonInventory;

public interface RestoreCallback {

    void onRestored(@NonNull CharonInventory inventory);

    void onRestoreFailed(@NonNull CharonError error);
}

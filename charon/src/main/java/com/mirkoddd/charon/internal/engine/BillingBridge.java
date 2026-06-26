package com.mirkoddd.charon.internal.engine;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.mirkoddd.charon.CharonError;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface BillingBridge {

    interface Action {
        void run(@NonNull com.android.billingclient.api.BillingClient client);
    }

    interface ConnectionErrorCallback {
        void onConnectionFailed(@NonNull CharonError error);
    }

    void executeWhenReady(@NonNull Action action, @NonNull ConnectionErrorCallback errorCallback);

    void showInAppMessages(@NonNull Activity activity);

    @NonNull
    com.android.billingclient.api.BillingClient getClient();

    @NonNull
    CharonLogger getLogger();
}

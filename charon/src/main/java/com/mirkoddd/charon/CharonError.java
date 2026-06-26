package com.mirkoddd.charon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.BillingClient;

public record CharonError(int code, @NonNull String message) {

    public CharonError(int code, @Nullable String message) {
        this.code = code;
        this.message = (message != null && !message.isBlank()) ? message : "Unknown billing error occurred";
    }

    public boolean isUserCanceled() {
        return code == BillingClient.BillingResponseCode.USER_CANCELED;
    }

    public boolean isRecoverable() {
        return code == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
               code == BillingClient.BillingResponseCode.NETWORK_ERROR;
    }

    public boolean requiresUserAction() {
        return code == BillingClient.BillingResponseCode.ERROR ||
               code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED;
    }

    public boolean isFatal() {
        return code == BillingClient.BillingResponseCode.DEVELOPER_ERROR ||
               code == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ||
               code == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE;
    }
}

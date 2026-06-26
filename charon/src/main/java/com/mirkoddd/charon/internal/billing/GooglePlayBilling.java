package com.mirkoddd.charon.internal.billing;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.InAppMessageParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.internal.engine.BillingBridge;
import com.mirkoddd.charon.internal.engine.CharonLogger;

import java.util.ArrayList;
import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class GooglePlayBilling implements BillingBridge {

    private final BillingClient billingClient;
    private final CharonLogger logger;
    private final List<Action> pendingActions = new ArrayList<>();
    private final List<ConnectionErrorCallback> errorCallbacks = new ArrayList<>();
    private boolean isConnecting = false;
    private int connectionRetries = 0;
    private static final int MAX_RETRIES = 3;

    public GooglePlayBilling(@NonNull Context context, @NonNull CharonLogger logger, @NonNull PurchasesUpdatedListener listener) {
        this.logger = logger;
        PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build();

        this.billingClient = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(listener)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build();
    }

    @Override
    @NonNull
    public CharonLogger getLogger() {
        return logger;
    }

    @Override
    public void executeWhenReady(@NonNull Action action, @NonNull ConnectionErrorCallback errorCallback) {
        synchronized (pendingActions) {
            if (billingClient.isReady()) {
                try {
                    action.run(billingClient);
                } catch (Exception e) {
                    logger.error("Action execution failed: " + e.getMessage());
                    errorCallback.onConnectionFailed(new CharonError(BillingClient.BillingResponseCode.ERROR, e.getMessage()));
                }
            } else {
                pendingActions.add(action);
                errorCallbacks.add(errorCallback);
                ensureConnection();
            }
        }
    }

    private void ensureConnection() {
        synchronized (pendingActions) {
            if (isConnecting || billingClient.isReady()) return;
            isConnecting = true;
        }

        logger.log("Connecting to Google Play Billing (Attempt " + (connectionRetries + 1) + ")...");
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                List<Action> actionsToRun;
                List<ConnectionErrorCallback> callbacksToNotify;

                synchronized (pendingActions) {
                    isConnecting = false;
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        connectionRetries = 0;
                        actionsToRun = new ArrayList<>(pendingActions);
                        callbacksToNotify = new ArrayList<>(errorCallbacks);
                        pendingActions.clear();
                        errorCallbacks.clear();
                    } else {
                        if (connectionRetries < MAX_RETRIES) {
                            connectionRetries++;
                            long delayMs = (long) Math.pow(2, connectionRetries) * 1000L;
                            logger.log("Connection failed: " + billingResult.getDebugMessage() + ". Retrying in " + delayMs + "ms...");
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> ensureConnection(), delayMs);
                            return;
                        }
                        connectionRetries = 0; // Reset for future attempts
                        actionsToRun = new ArrayList<>();
                        callbacksToNotify = new ArrayList<>(errorCallbacks);
                        pendingActions.clear();
                        errorCallbacks.clear();
                    }
                }

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    logger.log("Google Play Billing ready.");
                    for (Action action : actionsToRun) {
                        try {
                            action.run(billingClient);
                        } catch (Exception e) {
                            logger.error("Error executing deferred action: " + e.getMessage());
                        }
                    }
                } else {
                    logger.error("Google Play Billing connection failed permanently: " + billingResult.getDebugMessage());
                    CharonError error = new CharonError(billingResult.getResponseCode(), billingResult.getDebugMessage());
                    for (ConnectionErrorCallback cb : callbacksToNotify) {
                        cb.onConnectionFailed(error);
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                logger.log("Google Play Billing service disconnected.");
                synchronized (pendingActions) {
                    isConnecting = false;
                    if (!pendingActions.isEmpty()) {
                        logger.log("Actions pending. Attempting reconnection...");
                        ensureConnection();
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public BillingClient getClient() {
        return billingClient;
    }

    @Override
    public void showInAppMessages(@NonNull Activity activity) {
        executeWhenReady(client -> {
            InAppMessageParams params = InAppMessageParams.newBuilder()
                    .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
                    .build();
            client.showInAppMessages(activity, params, billingResult -> {
                logger.log("In-app message show result: " + billingResult.getResponseCode());
            });
        }, error -> logger.error("Failed to show in-app messages: " + error.message()));
    }
}

package com.mirkoddd.charon.checkout.internal;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.catalog.CharonPlan;
import com.mirkoddd.charon.checkout.CheckoutFlow;
import com.mirkoddd.charon.internal.engine.InternalFlowOrchestrator;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PlayStoreCheckoutWorkflow implements CheckoutFlow {

    private final InternalFlowOrchestrator orchestrator;
    private final CharonOffer offer;
    private final CharonPlan plan;

    public PlayStoreCheckoutWorkflow(@NonNull InternalFlowOrchestrator orchestrator, @NonNull CharonOffer offer) {
        this.orchestrator = orchestrator;
        this.offer = offer;
        this.plan = null;
    }

    public PlayStoreCheckoutWorkflow(@NonNull InternalFlowOrchestrator orchestrator, @NonNull CharonPlan plan) {
        this.orchestrator = orchestrator;
        this.offer = null;
        this.plan = plan;
    }

    @Override
    public void launch(@NonNull Activity activity) {
        if (offer != null) {
            orchestrator.launchInAppFlow(activity, offer);
        } else if (plan != null) {
            orchestrator.launchSubscriptionFlow(activity, plan);
        }
    }
}

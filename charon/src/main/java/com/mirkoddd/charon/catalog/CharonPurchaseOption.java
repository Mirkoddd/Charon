package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import com.mirkoddd.charon.CharonSku;

/**
 * Unified interface for any purchasable item in the catalog (In-App, Subscription, Rental).
 * This allows the UI and Checkout flows to handle different purchase types consistently.
 */
public interface CharonPurchaseOption {
    @NonNull String productId();
    @NonNull CharonOffer.Category category();
    @NonNull CharonSku skuConfig();
    @NonNull String offerToken();
    
    // Unified pricing access
    interface PricingProvider {
        @NonNull String currentPrice();
        long amountMicros();
    }
    
    @NonNull PricingProvider pricingInfo();
}

package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.mirkoddd.charon.CharonSku;

/**
 * The smart catalog provides a high-level API to discover products and their benefits.
 */
public record CharonCatalog(
        @NonNull List<CharonSubscription> subscriptions,
        @NonNull List<CharonInApp> consumables,
        @NonNull List<CharonInApp> nonConsumables) {

    public CharonCatalog(@NonNull List<CharonSubscription> subscriptions, @NonNull List<CharonInApp> inAppItems) {
        this(
                List.copyOf(subscriptions),
                List.copyOf(inAppItems.stream()
                        .filter(item -> item.type() == SkuType.CONSUMABLE)
                        .collect(Collectors.toList())),
                List.copyOf(inAppItems.stream()
                        .filter(item -> item.type() != SkuType.CONSUMABLE)
                        .collect(Collectors.toList()))
        );
    }

    public static CharonCatalog empty() {
        return new CharonCatalog(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return subscriptions.isEmpty() && consumables.isEmpty() && nonConsumables.isEmpty();
    }

    @NonNull
    public List<CharonInApp> allInApps() {
        return Stream.concat(consumables.stream(), nonConsumables.stream())
                .collect(Collectors.toList());
    }

    // --- Legacy compatibility & ID-based lookups ---

    @NonNull
    public Optional<CharonOffer> findOfferById(@NonNull String id) {
        return allInApps().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .map(CharonInApp::bestOfferOrStandard);
    }

    @NonNull
    public Optional<CharonPlan> findPlanById(@NonNull String id) {
        return subscriptions.stream()
                .filter(sub -> sub.id().equals(id))
                .findFirst()
                .map(sub -> sub.plans().get(0));
    }

    // --- Smart Identity-based Lookups ---

    @NonNull
    public Optional<CharonInApp> findInApp(@NonNull CharonSkuIdentity identity) {
        return allInApps().stream()
                .filter(item -> item.id().equals(identity.id()))
                .findFirst();
    }

    @NonNull
    public Optional<CharonSubscription> findSubscription(@NonNull CharonSkuIdentity identity) {
        return subscriptions.stream()
                .filter(sub -> sub.id().equals(identity.id()))
                .findFirst();
    }

    @NonNull
    public Optional<CharonOffer> findOffer(@NonNull CharonSkuIdentity identity) {
        return findInApp(identity).map(CharonInApp::bestOfferOrStandard);
    }

    @NonNull
    public Optional<CharonOffer> findOffer(@NonNull CharonSkuIdentity identity, @NonNull CharonOffer.Category category) {
        return findInApp(identity).flatMap(item -> item.bestOffer(category));
    }

    @NonNull
    public Optional<CharonPlan> findPlan(@NonNull CharonSkuIdentity identity) {
        return findSubscription(identity).map(sub -> sub.plans().get(0));
    }

    @NonNull
    public Optional<CharonPlan> findPlan(@NonNull CharonSkuIdentity identity, @NonNull BillingPeriod period) {
        return findSubscription(identity).flatMap(sub -> sub.bestPlan(period));
    }

    // --- Bulk Filters ---

    @NonNull
    public List<CharonInApp> findInAppsByCategory(@NonNull CharonOffer.Category category) {
        return allInApps().stream()
                .filter(item -> item.offers().stream().anyMatch(o -> o.category() == category))
                .collect(Collectors.toList());
    }

    @NonNull
    public List<CharonSubscription> findSubscriptionsByPeriod(@NonNull BillingPeriod period) {
        return subscriptions.stream()
                .filter(sub -> sub.plans().stream().anyMatch(p -> p.pricing().billingPeriod() == period))
                .collect(Collectors.toList());
    }

    // --- Semantic Entitlement-based Discovery ---

    /**
     * Finds all purchase options that unlock a specific entitlement.
     * This abstracts away the product type (In-App, Subscription, Rental) to focus on the benefit.
     */
    @NonNull
    public List<CharonPurchaseOption> findOptionsFor(@NonNull CharonEntitlement entitlement) {
        List<CharonPurchaseOption> options = new ArrayList<>();
        
        // Find in-apps
        for (CharonInApp item : allInApps()) {
            if (item.offers().stream().anyMatch(o -> o.skuConfig().entitlements().contains(entitlement.id()))) {
                options.addAll(item.offers());
            }
        }
        
        // Find subscriptions
        for (CharonSubscription sub : subscriptions) {
            for (CharonPlan plan : sub.plans()) {
                if (plan.skuConfig().entitlements().contains(entitlement.id())) {
                    options.add(plan);
                }
            }
        }
        
        return options;
    }

    /**
     * Finds the best (recommended/cheapest) purchase option for a specific entitlement.
     */
    @NonNull
    public Optional<CharonPurchaseOption> findBestOptionFor(@NonNull CharonEntitlement entitlement) {
        return findOptionsFor(entitlement).stream()
                .min(java.util.Comparator.comparingLong(o -> o.pricingInfo().amountMicros()));
    }
}

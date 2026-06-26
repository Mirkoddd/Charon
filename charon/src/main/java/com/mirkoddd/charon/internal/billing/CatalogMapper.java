package com.mirkoddd.charon.internal.billing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.android.billingclient.api.ProductDetails;
import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.catalog.BillingPeriod;
import com.mirkoddd.charon.catalog.CharonInApp;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.catalog.CharonPlan;
import com.mirkoddd.charon.catalog.CharonSubscription;
import com.mirkoddd.charon.internal.engine.CharonLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class CatalogMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private CatalogMapper() {
    }

    @Nullable
    public static CharonSubscription mapSubscription(@NonNull ProductDetails pd, @NonNull CharonSku skuConfig, boolean isAlreadyInGroup) {
        List<ProductDetails.SubscriptionOfferDetails> allOfferDetails = pd.getSubscriptionOfferDetails();
        if (allOfferDetails == null || allOfferDetails.isEmpty()) {
            return null;
        }

        Map<String, CharonPlan> bestPlans = new HashMap<>();

        for (ProductDetails.SubscriptionOfferDetails offer : allOfferDetails) {
            ProductDetails.PricingPhases pricingPhases = offer.getPricingPhases();
            CharonPlan.Pricing pricing = createSubscriptionPricing(pricingPhases.getPricingPhaseList());
            if (pricing == null) {
                continue;
            }

            String baseId = offer.getBasePlanId();
            CharonPlan candidate = new CharonPlan(skuConfig, baseId, offer.getOfferId(), offer.getOfferToken(), pricing);

            if (!bestPlans.containsKey(baseId)) {
                bestPlans.put(baseId, candidate);
            } else {
                CharonPlan currentBest = bestPlans.get(baseId);
                if (currentBest != null && shouldReplace(currentBest.pricing(), pricing, isAlreadyInGroup)) {
                    bestPlans.put(baseId, candidate);
                }
            }
        }

        List<CharonPlan> plans = new ArrayList<>(bestPlans.values());
        return (plans.isEmpty()) ? null : new CharonSubscription(pd.getProductId(), pd.getTitle(), pd.getDescription(), plans);
    }

    private static boolean shouldReplace(@NonNull CharonPlan.Pricing current, @NonNull CharonPlan.Pricing candidate, boolean isAlreadyInGroup) {
        if (isAlreadyInGroup) {
            if (candidate instanceof CharonPlan.Discounted) {
                return true;
            }
            return current instanceof CharonPlan.FreeTrial && candidate instanceof CharonPlan.Standard;
        } else {
            if (candidate instanceof CharonPlan.FreeTrial) {
                return true;
            }
            return current instanceof CharonPlan.Discounted && candidate instanceof CharonPlan.Standard;
        }
    }

    @Nullable
    private static CharonPlan.Pricing createSubscriptionPricing(@Nullable List<ProductDetails.PricingPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return null;
        }

        ProductDetails.PricingPhase current = phases.get(0);
        ProductDetails.PricingPhase base = phases.get(phases.size() - 1);
        BillingPeriod billingPeriod = BillingPeriod.fromIsoString(base.getBillingPeriod());

        if (phases.size() == 1) {
            return new CharonPlan.Standard(current.getFormattedPrice(), billingPeriod);
        }

        if (current.getPriceAmountMicros() == 0) {
            return new CharonPlan.FreeTrial(
                    current.getFormattedPrice(),
                    base.getFormattedPrice(),
                    billingPeriod,
                    BillingPeriod.fromIsoString(current.getBillingPeriod())
            );
        }

        return new CharonPlan.Discounted(current.getFormattedPrice(), base.getFormattedPrice(), billingPeriod);
    }

    @NonNull
    public static CharonInApp mapInApp(@NonNull ProductDetails pd, @NonNull CharonSku skuConfig, @NonNull CharonLogger logger) {
        List<ProductDetails.OneTimePurchaseOfferDetails> googleOffers = pd.getOneTimePurchaseOfferDetailsList();

        if (googleOffers == null || googleOffers.isEmpty()) {
            ProductDetails.OneTimePurchaseOfferDetails go = pd.getOneTimePurchaseOfferDetails();
            String price = (go != null) ? go.getFormattedPrice() : "N/A";
            long micros = (go != null) ? go.getPriceAmountMicros() : 0;
            String token = (go != null) ? go.getOfferToken() : "";
            if (token == null) {
                token = "";
            }

            List<CharonOffer> offers = List.of(new CharonOffer(skuConfig, "default", null, token, new CharonOffer.Standard(price, micros), new CharonOffer.Purchase()));
            return new CharonInApp(pd.getProductId(), pd.getTitle(), pd.getDescription(), offers, skuConfig.type());
        }

        Map<Long, String> priceMap = new HashMap<>();
        for (ProductDetails.OneTimePurchaseOfferDetails go : googleOffers) {
            if (go != null) {
                priceMap.put(go.getPriceAmountMicros(), go.getFormattedPrice());
            }
        }

        List<CharonOffer> offers = new ArrayList<>();
        for (ProductDetails.OneTimePurchaseOfferDetails go : googleOffers) {
            if (go == null) {
                continue;
            }

            CharonOffer.Pricing pricing;
            String currentPrice = go.getFormattedPrice();
            String fullPrice = priceMap.get(go.getFullPriceMicros());

            String purchaseOptionId = go.getPurchaseOptionId() != null ? go.getPurchaseOptionId() : "default";

            StringBuilder logBuilder = new StringBuilder("Mapping InApp for ").append(pd.getProductId());
            logBuilder.append("\n  -> Option: ").append(purchaseOptionId);
            if (go.getOfferId() != null) {
                logBuilder.append(" (Offer: ").append(go.getOfferId()).append(")");
            }

            long fullPriceMicros = go.getFullPriceMicros() != null ? go.getFullPriceMicros() : 0;
            if (fullPrice != null && go.getPriceAmountMicros() < fullPriceMicros) {
                int percentage = 0;
                String formattedDiscount = null;
                long discountMicros = fullPriceMicros - go.getPriceAmountMicros();
                String discountCurrency = go.getPriceCurrencyCode();
                CharonOffer.TimeWindow validity = null;

                ProductDetails.OneTimePurchaseOfferDetails.DiscountDisplayInfo discountInfo = go.getDiscountDisplayInfo();
                if (discountInfo != null) {
                    percentage = discountInfo.getPercentageDiscount() != null ? discountInfo.getPercentageDiscount() : 0;
                    ProductDetails.OneTimePurchaseOfferDetails.DiscountDisplayInfo.DiscountAmount amount = discountInfo.getDiscountAmount();
                    if (amount != null) {
                        formattedDiscount = amount.getFormattedDiscountAmount();
                        discountMicros = amount.getDiscountAmountMicros();
                        discountCurrency = amount.getDiscountAmountCurrencyCode();
                    }
                }

                ProductDetails.OneTimePurchaseOfferDetails.ValidTimeWindow timeWindow = go.getValidTimeWindow();
                if (timeWindow != null) {
                    long start = timeWindow.getStartTimeMillis() != null ? timeWindow.getStartTimeMillis() : 0;
                    long end = timeWindow.getEndTimeMillis() != null ? timeWindow.getEndTimeMillis() : 0;
                    if (end > 0 && System.currentTimeMillis() > end) {
                        logger.log("Skipping expired offer for " + pd.getProductId() + " (Expired at " + DATE_FORMAT.format(Instant.ofEpochMilli(end)) + ")");
                        continue;
                    }
                    validity = new CharonOffer.TimeWindow(start, end);
                }
                pricing = new CharonOffer.Discounted(currentPrice, go.getPriceAmountMicros(), fullPrice, new CharonOffer.Discount(percentage, formattedDiscount, discountMicros, discountCurrency), validity);
                logBuilder.append("\n  -> DISCOUNTED: ").append(currentPrice).append(" (was ").append(fullPrice).append(")");

                if (percentage > 0) {
                    logBuilder.append(" - ").append(percentage).append("% off");
                }

                if (formattedDiscount != null) {
                    logBuilder.append(" (Save ").append(formattedDiscount).append(")");
                } else if (discountMicros > 0) {
                    double displayAmount = discountMicros / 1000000.0;
                    logBuilder.append(String.format(Locale.getDefault(), " (Save %.2f %s)", displayAmount, discountCurrency));
                }

                if (validity != null && validity.endMillis() > 0) {
                    logBuilder.append("\n  -> VALID UNTIL: ").append(DATE_FORMAT.format(Instant.ofEpochMilli(validity.endMillis())));
                }
            } else {
                pricing = new CharonOffer.Standard(currentPrice, go.getPriceAmountMicros());
                logBuilder.append("\n  -> STANDARD: ").append(currentPrice);
            }

            CharonOffer.Mode mode = getMode(go, logBuilder);

            logger.log(logBuilder.toString());

            String token = go.getOfferToken();
            if (token == null) {
                token = "";
            }

            offers.add(new CharonOffer(skuConfig, purchaseOptionId, go.getOfferId(), token, pricing, mode));
        }

        return new CharonInApp(pd.getProductId(), pd.getTitle(), pd.getDescription(), List.copyOf(offers), skuConfig.type());
    }

    private static CharonOffer.Mode getMode(ProductDetails.OneTimePurchaseOfferDetails go, StringBuilder logBuilder) {
        CharonOffer.Mode mode = new CharonOffer.Purchase();
        ProductDetails.OneTimePurchaseOfferDetails.LimitedQuantityInfo limited = go.getLimitedQuantityInfo();
        if (limited != null) {
            int max = limited.getMaximumQuantity();
            int rem = limited.getRemainingQuantity();
            mode = new CharonOffer.Limited(max, rem);
            logBuilder.append("\n  -> MODE: Limited (").append(rem).append("/").append(max).append(")");
        }

        ProductDetails.OneTimePurchaseOfferDetails.RentalDetails rental = go.getRentalDetails();
        if (rental != null) {
            String period = rental.getRentalPeriod();
            String startPeriod = rental.getRentalExpirationPeriod() != null ? rental.getRentalExpirationPeriod() : "N/A";
            mode = new CharonOffer.Rental(period, startPeriod);
            logBuilder.append("\n  -> MODE: Rental (Duration: ").append(period).append(", Must start within: ").append(startPeriod).append(")");
        }
        return mode;
    }
}

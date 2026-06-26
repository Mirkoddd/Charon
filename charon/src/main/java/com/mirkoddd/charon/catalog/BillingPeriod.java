package com.mirkoddd.charon.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

public enum BillingPeriod {
    WEEKLY,
    MONTHLY,
    THREE_MONTHS,
    SIX_MONTHS,
    YEARLY,
    UNKNOWN;

    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static BillingPeriod fromIsoString(@NonNull String iso) {
        if (iso.isEmpty()) return UNKNOWN;

        return switch (iso) {
            case "P1W" -> WEEKLY;
            case "P1M" -> MONTHLY;
            case "P3M" -> THREE_MONTHS;
            case "P6M" -> SIX_MONTHS;
            case "P1Y" -> YEARLY;
            default -> UNKNOWN;
        };
    }

}

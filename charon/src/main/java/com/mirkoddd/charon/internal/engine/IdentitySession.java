package com.mirkoddd.charon.internal.engine;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public record IdentitySession(@Nullable String accountId, @Nullable String profileId) {
    public static final IdentitySession EMPTY = new IdentitySession(null, null);

    public boolean isEmpty() {
        return accountId == null && profileId == null;
    }
}

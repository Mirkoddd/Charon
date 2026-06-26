package com.mirkoddd.charon.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class IdentityManager {
    private IdentitySession currentSession = IdentitySession.EMPTY;

    public boolean update(@Nullable String accountId, @Nullable String profileId) {
        IdentitySession newSession = new IdentitySession(accountId, profileId);
        if (newSession.equals(currentSession)) {
            return false;
        }
        this.currentSession = newSession;
        return true;
    }

    @NonNull
    public IdentitySession getCurrentSession() {
        return currentSession;
    }
}

package com.mirkoddd.charon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.catalog.SkuType;
import com.mirkoddd.charon.checkout.UpgradeMode;
import java.util.Collections;
import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CharonSku {

    @NonNull
    String id();

    @NonNull
    SkuType type();

    @Nullable
    default String groupId() {
        return null;
    }

    default int weight() {
        return 0;
    }

    @NonNull
    default List<String> entitlements() {
        return Collections.emptyList();
    }

    default UpgradeMode upgradeMode() {
        return UpgradeMode.TIME_PRORATION;
    }
}

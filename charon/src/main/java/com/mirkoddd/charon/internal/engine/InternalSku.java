package com.mirkoddd.charon.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.CharonSku;
import com.mirkoddd.charon.catalog.SkuType;
import com.mirkoddd.charon.checkout.UpgradeMode;

import java.util.Collections;
import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class InternalSku implements CharonSku {
    private final String id;
    private final SkuType type;
    private final List<String> entitlements;

    protected InternalSku(String id, SkuType type, List<String> entitlements) {
        this.id = id;
        this.type = type;
        this.entitlements = entitlements != null
                ? List.copyOf(entitlements)
                : Collections.emptyList();
    }

    @NonNull @Override public String id() { return id; }
    @NonNull @Override public SkuType type() { return type; }
    @NonNull @Override public List<String> entitlements() { return entitlements; }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final class Subscription extends InternalSku {
        private final String groupId;
        private final int weight;
        private final UpgradeMode upgradeMode;

        public Subscription(String id, String groupId, int weight, List<String> entitlements,
                     UpgradeMode upgradeMode) {
            super(id, SkuType.SUBSCRIPTION, entitlements);
            this.groupId = groupId;
            this.weight = weight;
            this.upgradeMode = upgradeMode;
        }

        @Nullable @Override public String groupId() { return groupId; }
        @Override public int weight() { return weight; }
        @Override public UpgradeMode upgradeMode() { return upgradeMode; }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final class Consumable extends InternalSku {
        public Consumable(String id) {
            super(id, SkuType.CONSUMABLE, Collections.emptyList());
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final class NonConsumable extends InternalSku {
        public NonConsumable(String id, List<String> entitlements) {
            super(id, SkuType.NON_CONSUMABLE, entitlements);
        }
    }
}

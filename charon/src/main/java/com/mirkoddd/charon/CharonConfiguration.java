package com.mirkoddd.charon;

import com.mirkoddd.charon.catalog.CharonSkuIdentity;
import com.mirkoddd.charon.catalog.CharonEntitlement;
import com.mirkoddd.charon.checkout.internal.Security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.mirkoddd.charon.annotations.Consumable;
import com.mirkoddd.charon.annotations.NonConsumable;
import com.mirkoddd.charon.annotations.Subscription;
import com.mirkoddd.charon.internal.engine.CharonEngine;
import com.mirkoddd.charon.internal.engine.CharonLogger;
import com.mirkoddd.charon.catalog.SkuType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CharonConfiguration {

    private final String licenseKey;
    private final CharonInterceptor interceptor;
    private final int fulfillmentTimeoutSeconds;
    private final CharonLogger logger;
    private final Map<String, CharonSku> skuRegistry;

    private CharonConfiguration(Builder builder) {
        this.licenseKey = builder.licenseKey;
        this.interceptor = builder.interceptor;
        this.fulfillmentTimeoutSeconds = builder.fulfillmentTimeoutSeconds;
        this.logger = builder.logger != null ? builder.logger : new DefaultLogger(builder.loggingEnabled);
        this.skuRegistry = Map.copyOf(builder.skuRegistry);
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public CharonInterceptor getInterceptor() {
        return interceptor;
    }

    public int getFulfillmentTimeoutSeconds() {
        return fulfillmentTimeoutSeconds;
    }

    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public CharonLogger getLogger() {
        return logger;
    }

    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public CharonSku getSku(@NonNull String id) {
        return skuRegistry.get(id);
    }

    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getSubscriptionIds() {
        return skuRegistry.values().stream()
                .filter(sku -> sku.type() == SkuType.SUBSCRIPTION)
                .map(CharonSku::id)
                .collect(Collectors.toList());
    }

    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getInAppIds() {
        return skuRegistry.values().stream()
                .filter(sku -> sku.type() == SkuType.CONSUMABLE || sku.type() == SkuType.NON_CONSUMABLE)
                .map(CharonSku::id)
                .collect(Collectors.toList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String licenseKey = "";
        private boolean loggingEnabled = false;
        private CharonInterceptor interceptor;
        private int fulfillmentTimeoutSeconds = 15;
        private final Map<String, CharonSku> skuRegistry = new HashMap<>();
        private final Set<String> registeredEntitlements = new HashSet<>();
        private CharonLogger logger;

        private Builder() {
        }

        public void setLogger(CharonLogger logger) {
            this.logger = logger;
        }

        public final <E extends Enum<E> & CharonEntitlement> void registerEntitlements(@NonNull Class<E> enumClass) {
            E[] constants = enumClass.getEnumConstants();
            if (constants != null) {
                for (E constant : constants) {
                    registeredEntitlements.add(constant.id());
                }
            }
        }

        public final <I extends Enum<I> & CharonSkuIdentity> void registerCatalog(@NonNull Class<I> enumClass) {
            I[] constants = enumClass.getEnumConstants();
            if (constants == null) {
                throw new IllegalArgumentException("Charon Configuration Error: " + enumClass.getName() + " is not an enum.");
            }

            for (I constant : constants) {
                try {
                    Field field = enumClass.getDeclaredField(constant.name());
                    CharonSku sku = mapFieldToSku(constant.id(), field);
                    if (sku != null) {
                        skuRegistry.put(sku.id(), sku);
                    }
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException("Charon Internal Error: Could not find field for enum constant " + constant.name(), e);
                }
            }
        }

        private CharonSku mapFieldToSku(String id, Field field) {
            Subscription subAnn = field.getAnnotation(Subscription.class);
            if (subAnn != null) {
                return CharonEngine.createSubscription(id, subAnn.groupId(), subAnn.weight(), List.of(subAnn.entitlements()), subAnn.upgradeMode());
            }

            Consumable consAnn = field.getAnnotation(Consumable.class);
            if (consAnn != null) {
                return CharonEngine.createConsumable(id);
            }

            NonConsumable nonConsAnn = field.getAnnotation(NonConsumable.class);
            if (nonConsAnn != null) {
                return CharonEngine.createNonConsumable(id, List.of(nonConsAnn.entitlements()));
            }

            return null;
        }

        public void setLicenseKey(String key) {
            if (key != null && !key.isBlank()) {
                this.licenseKey = key;
            }
        }

        public void enableLogging(boolean enable) {
            this.loggingEnabled = enable;
        }

        public void setInterceptor(CharonInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        public void setInterceptor(CharonInterceptor interceptor, int timeoutSeconds) {
            this.interceptor = interceptor;
            this.fulfillmentTimeoutSeconds = timeoutSeconds;
        }

        public CharonConfiguration build() {
            validateRegistry();
            validateEntitlements();
            validateLicenseKey();
            return new CharonConfiguration(this);
        }

        private void validateRegistry() {
            if (skuRegistry.isEmpty()) {
                throw new IllegalStateException("Charon configuration invalid: No annotated SKUs found.");
            }
        }

        private void validateEntitlements() {
            if (registeredEntitlements.isEmpty()) return;

            for (CharonSku sku : skuRegistry.values()) {
                for (String entitlement : sku.entitlements()) {
                    if (!registeredEntitlements.contains(entitlement)) {
                        throw new IllegalStateException("Charon configuration error: Entitlement '" + entitlement + 
                            "' declared for SKU '" + sku.id() + "' is not present in the registered CharonEntitlement enum.");
                    }
                }
            }
        }

        private void validateLicenseKey() {
            if (licenseKey != null && !licenseKey.isBlank()) {
                if (!Security.isValidPublicKey(licenseKey)) {
                    throw new IllegalArgumentException("Charon configuration invalid: The provided licenseKey is not a valid RSA Public Key.");
                }
            }
        }
    }
}

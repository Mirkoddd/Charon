package com.mirkoddd.charon;

import com.mirkoddd.charon.catalog.CharonSkuIdentity;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.mirkoddd.charon.checkout.internal.PurchaseFulfiller;
import com.mirkoddd.charon.internal.billing.GooglePlayBilling;
import com.mirkoddd.charon.internal.engine.CharonEngine;
import com.mirkoddd.charon.internal.engine.CharonLogger;
import com.mirkoddd.charon.internal.engine.FlowLauncher;
import com.mirkoddd.charon.internal.engine.IdentityManager;
import com.mirkoddd.charon.internal.engine.PurchaseRecoverer;
import com.mirkoddd.charon.inventory.internal.PlayStoreInventory;
import com.mirkoddd.charon.catalog.internal.PlayStoreCatalog;

import java.util.List;

public final class CharonBuilder {

    private CharonBuilder() {}

    static SkuConfigurator create(@NonNull Application application) {
        return new BuilderImpl(application);
    }

    public interface SkuConfigurator {
        @NonNull
        <E extends Enum<E> & CharonSkuIdentity> CatalogConfigured registerSkus(@NonNull Class<E> enumClass);
        @NonNull
        <E extends Enum<E> & com.mirkoddd.charon.catalog.CharonEntitlement> SkuConfigurator registerEntitlements(@NonNull Class<E> enumClass);
    }

    public interface CatalogConfigured extends InterceptorConfigurator {
        @NonNull
        InterceptorConfigurator setLicenseKey(@NonNull String key);
    }

    public interface InterceptorConfigurator extends Buildable {
        @NonNull
        Buildable setInterceptor(@NonNull CharonInterceptor interceptor);
        @NonNull
        Buildable setInterceptor(@NonNull CharonInterceptor interceptor, int timeoutSeconds);
    }

    public interface Buildable {
        @NonNull
        Buildable setLogger(@NonNull CharonLogger logger);
        @NonNull
        Buildable enableLogging(boolean enable);
        void buildAndConnect();
    }

    private static class BuilderImpl implements SkuConfigurator, CatalogConfigured, InterceptorConfigurator, Buildable {
        private final Application application;
        private final CharonConfiguration.Builder configBuilder;

        BuilderImpl(@NonNull Application application) {
            this.application = application;
            this.configBuilder = CharonConfiguration.builder();
        }

        @NonNull
        @Override
        public <E extends Enum<E> & CharonSkuIdentity> CatalogConfigured registerSkus(@NonNull Class<E> enumClass) {
            configBuilder.registerCatalog(enumClass);
            return this;
        }

        @NonNull
        @Override
        public <E extends Enum<E> & com.mirkoddd.charon.catalog.CharonEntitlement> SkuConfigurator registerEntitlements(@NonNull Class<E> enumClass) {
            configBuilder.registerEntitlements(enumClass);
            return this;
        }

        @NonNull
        @Override
        public InterceptorConfigurator setLicenseKey(@NonNull String key) {
            configBuilder.setLicenseKey(key);
            return this;
        }

        @NonNull
        @Override
        public Buildable setLogger(@NonNull CharonLogger logger) {
            configBuilder.setLogger(logger);
            return this;
        }

        @NonNull
        @Override
        public Buildable enableLogging(boolean enable) {
            configBuilder.enableLogging(enable);
            return this;
        }

        @NonNull
        @Override
        public Buildable setInterceptor(@NonNull CharonInterceptor interceptor) {
            configBuilder.setInterceptor(interceptor);
            return this;
        }

        @NonNull
        @Override
        public Buildable setInterceptor(@NonNull CharonInterceptor interceptor, int timeoutSeconds) {
            configBuilder.setInterceptor(interceptor, timeoutSeconds);
            return this;
        }

        @Override
        public void buildAndConnect() {
            CharonConfiguration finalConfig = configBuilder.build();
            PurchasesUpdatedProxy proxy = new PurchasesUpdatedProxy();
            CharonEngine engine = createEngine(finalConfig, proxy);
            
            proxy.setDelegate(engine);
            Charon.setInstance(engine);
        }

        private CharonEngine createEngine(CharonConfiguration finalConfig, PurchasesUpdatedProxy proxy) {
            CharonLogger logger = finalConfig.getLogger();
            GooglePlayBilling bridge = new GooglePlayBilling(application, logger, proxy);
            PlayStoreInventory store = new PlayStoreInventory(bridge, finalConfig, logger);
            PlayStoreCatalog catalog = new PlayStoreCatalog(bridge, store, finalConfig, logger);
            
            IdentityManager identityManager = new IdentityManager();
            PurchaseFulfiller fulfiller = new PurchaseFulfiller(bridge.getClient(), finalConfig, logger);
            PurchaseRecoverer recoverer = new PurchaseRecoverer(store, fulfiller, identityManager, logger);
            FlowLauncher flowLauncher = new FlowLauncher(bridge, store, finalConfig, identityManager, logger);

            return new CharonEngine(store, catalog, identityManager, recoverer, flowLauncher, fulfiller, logger);
        }
    }

    private static class PurchasesUpdatedProxy implements PurchasesUpdatedListener {
        private PurchasesUpdatedListener delegate;

        void setDelegate(PurchasesUpdatedListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
            if (delegate != null) {
                delegate.onPurchasesUpdated(billingResult, purchases);
            }
        }
    }
}

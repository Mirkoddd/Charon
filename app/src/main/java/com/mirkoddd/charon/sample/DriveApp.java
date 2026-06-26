package com.mirkoddd.charon.sample;

import android.app.Application;

import com.mirkoddd.charon.Charon;
import com.mirkoddd.charon.sample.config.AppEntitlements;
import com.mirkoddd.charon.sample.config.AppCatalog;

/**
 * Created by Mirko Dimartino on 13/04/26.
 */
public class DriveApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Charon.init(this)
                .registerEntitlements(AppEntitlements.class) // optional, used to check entitlement status and find offers by entitlement
                .registerSkus(AppCatalog.class)
                .setLicenseKey(BuildConfig.PLAY_CONSOLE_KEY)// optional
//                .setInterceptor(new FakeDelayInterceptor()) // optional server side validation
                .enableLogging(true) // optional, for debug logs
                .buildAndConnect();

        Charon.getInstance().setUserIdentity("driver_1"); //TODO explain centralized user identity management


//        Charon.getInstance().clearUserIdentity(); // clear user identity, affects inventory status
    }
}

package com.mirkoddd.charon.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.mirkoddd.charon.checkout.UpgradeMode;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Subscription {
    String groupId();
    int weight() default 0;
    String[] entitlements() default {};
    UpgradeMode upgradeMode() default UpgradeMode.TIME_PRORATION;
}
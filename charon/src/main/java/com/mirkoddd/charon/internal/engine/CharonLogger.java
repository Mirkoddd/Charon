package com.mirkoddd.charon.internal.engine;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CharonLogger {
    void log(String message);
    void error(String message);
}
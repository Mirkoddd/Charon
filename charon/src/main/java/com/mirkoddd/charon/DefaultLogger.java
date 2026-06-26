package com.mirkoddd.charon;

import android.util.Log;
import androidx.annotation.RestrictTo;
import com.mirkoddd.charon.internal.engine.CharonLogger;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
final class DefaultLogger implements CharonLogger {

    private static final String TAG = "CharonLog";
    private final boolean isEnabled;

    DefaultLogger(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Override
    public void log(String message) {
        if (isEnabled) {
            Log.d(TAG, message);
        }
    }

    @Override
    public void error(String message) {
        if (isEnabled) {
            Log.e(TAG, message);
        }
    }
}

package com.mirkoddd.charon.checkout.internal;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class Security {
    private static final String TAG = "CharonSecurity";
    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    private Security() {
    }

    public static boolean isValidPublicKey(@Nullable String publicKey) {
        if (TextUtils.isEmpty(publicKey)) return false;
        try {
            generatePublicKey(publicKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyPurchase(@Nullable String base64PublicKey, @Nullable String signedData, @Nullable String signature) {
        if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey) || TextUtils.isEmpty(signature)) {
            Log.e(TAG, "Purchase verification failed: missing data.");
            return false;
        }

        try {
            PublicKey key = generatePublicKey(base64PublicKey);
            return verify(key, signedData, signature);
        } catch (Exception e) {
            Log.e(TAG, "Exception verifying signature: " + e.getMessage());
            return false;
        }
    }

    private static PublicKey generatePublicKey(String encodedPublicKey) throws Exception {
        byte[] decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
        return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    private static boolean verify(PublicKey publicKey, String signedData, String signature) {
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.decode(signature, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Base64 decoding failed.");
            return false;
        }
        try {
            Signature signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM);
            signatureAlgorithm.initVerify(publicKey);
            signatureAlgorithm.update(signedData.getBytes());
            if (!signatureAlgorithm.verify(signatureBytes)) {
                Log.e(TAG, "Signature verification failed.");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception during verification: " + e.getMessage());
        }
        return false;
    }
}
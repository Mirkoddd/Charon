package com.mirkoddd.charon.observation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.inventory.CharonPurchase;
import java.util.function.Consumer;

public sealed interface CharonCheckout {

    record Idle() implements CharonCheckout {}

    record Validating(@Nullable CharonPurchase purchase) implements CharonCheckout {}

    record Pending(@NonNull CharonPurchase purchase) implements CharonCheckout {}

    record Success(@NonNull CharonPurchase purchase) implements CharonCheckout {}

    record Failed(@NonNull CharonError error) implements CharonCheckout {}

    record UserCanceled() implements CharonCheckout {}

    default void handle(
            @NonNull Consumer<Success> onSuccess,
            @NonNull Consumer<Failed> onFailure,
            @NonNull Consumer<Validating> onValidating,
            @NonNull Consumer<Pending> onPending,
            @NonNull Runnable onOthers) {
        if (this instanceof Success s) onSuccess.accept(s);
        else if (this instanceof Failed f) onFailure.accept(f);
        else if (this instanceof Validating v) onValidating.accept(v);
        else if (this instanceof Pending p) onPending.accept(p);
        else onOthers.run();
    }
}

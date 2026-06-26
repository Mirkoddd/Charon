package com.mirkoddd.charon.sample.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import com.mirkoddd.charon.sample.R;
import com.mirkoddd.charon.sample.databinding.ViewDriveBinding;

/**
 * Created by Mirko Dimartino on 12/06/26.
 */
public class DriveView extends LinearLayout {
    public interface OnDriveListener {
        void onDriveCompleted();
    }
    private final ViewDriveBinding binding;
    private final Vehicle vehicle;
    private boolean isVehicleDriving;

    private OnDriveListener onDriveListener;

    public void setOnDriveListener(OnDriveListener onDriveListener){
        this.onDriveListener = onDriveListener;
    }

    public DriveView(Context context) {
        this(context, null);
    }

    public DriveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DriveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        vehicle = new Vehicle(context);

        binding = ViewDriveBinding.inflate(LayoutInflater.from(context), this, true);

        binding.progressIndicator.setMax(vehicle.getMaxSteps());
        binding.progressIndicator.setProgress(vehicle.getCurrentPos(), false);

        binding.fuelLevel.setMax(vehicle.getFuelTankMaxCapacity());
        binding.fuelLevel.setProgress(vehicle.getFuelLevel(), false);


        binding.track.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        binding.track.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        float maxDistance = binding.track.getWidth() - binding.vehicleContainer.getWidth();
                        vehicle.setMaxDistance(maxDistance);

                        binding.vehicleContainer.setTranslationX(vehicle.getDistance());
                        if (onDriveListener != null) onDriveListener.onDriveCompleted();
                    }
                });
    }

    public void advance() {
        if (isVehicleDriving)return;

        var container = binding.vehicleContainer;

        vehicle.drive();
        
        if (vehicle.getCurrentPos() == 0){
            binding.fuelLevel.setProgress(vehicle.getFuelLevel());
            binding.progressIndicator.setProgress(vehicle.getCurrentPos());

            container.setTranslationX(vehicle.getDistance());
            if (onDriveListener != null) onDriveListener.onDriveCompleted();

            return;
        }

        binding.fuelLevel.setProgress(vehicle.getFuelLevel(), false);

        binding.progressIndicator.setProgress(vehicle.getCurrentPos(), false);


        container.animate().setDuration(500)
                .translationX(vehicle.getDistance())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        isVehicleDriving = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        isVehicleDriving = false;
                        if (onDriveListener != null) onDriveListener.onDriveCompleted();
                    }
                });

    }

    public void refill() {
        vehicle.refill();
        binding.fuelLevel.setProgress(vehicle.getFuelLevel(), false);
    }

    public boolean isOutOfFuel() {
        return vehicle.isOutOfFuel();
    }

    public boolean isTargetReached(){
        return vehicle.isTagetReached();
    }

    public void setVehicleMoto() {
        binding.vehicleType.setImageResource(R.drawable.ic_motorcycle);
    }

    public void setVehicleDefault() {
        binding.vehicleType.setImageResource(R.drawable.ic_vespa);
    }


}

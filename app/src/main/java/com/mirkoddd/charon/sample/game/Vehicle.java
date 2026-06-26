package com.mirkoddd.charon.sample.game;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Mirko Dimartino on 12/06/26.
 */
public class Vehicle {

    private static final String PREF_NAME = "vehicle";
    private static final String CURRENT_POS = "current_pos";
    private static final int MAX_STEPS = 5;
    private float maxDistance;
    private final SharedPreferences.Editor editor;
    private final SharedPreferences prefs;
    private final FuelTank fuelTank;

    public Vehicle(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        editor = prefs.edit();
        fuelTank = new FuelTank(context);
    }

    public int getFuelTankMaxCapacity() {
        return fuelTank.getMaxTankCapacity();
    }

    public int getFuelLevel() {
        return fuelTank.getFuelLevel();
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public float getDistance() {
        float steps = maxDistance / MAX_STEPS;
        return steps * getCurrentPos();
    }

    public void drive() {
        if (!isTagetReached()) fuelTank.consumeFuel();
        updateCurrentPosition();
    }

    public void refill() {
        fuelTank.refillWholeFuelTank();
    }

    public boolean isOutOfFuel() {
        return fuelTank.getFuelLevel() == 0;
    }

    public int getCurrentPos() {
        return prefs.getInt(CURRENT_POS, 0);
    }

    private void updateCurrentPosition() {
        int newPosition = getCurrentPos() + 1;
        editor.putInt(CURRENT_POS, newPosition % (MAX_STEPS + 1));
        editor.commit();
    }


    public boolean isTagetReached() {
        return getCurrentPos() == MAX_STEPS;
    }

    public int getMaxSteps() {
        return MAX_STEPS;
    }
}

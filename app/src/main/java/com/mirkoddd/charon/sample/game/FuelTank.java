package com.mirkoddd.charon.sample.game;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Mirko Dimartino on 12/06/26.
 */
public class FuelTank {

    private static final String PREF_NAME = "tank__";
    private static final String FUEL_LEVEL = "fuel_level";

    private static final int MAX_TANK_CAPACITY = 4;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public FuelTank(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        editor = prefs.edit();
    }

    public int getMaxTankCapacity() {
        return MAX_TANK_CAPACITY;
    }

    public int getFuelLevel() {
        return prefs.getInt(FUEL_LEVEL, MAX_TANK_CAPACITY);
    }

    public void refillWholeFuelTank() {
        editor.putInt(FUEL_LEVEL, MAX_TANK_CAPACITY);
        editor.commit();
    }

    public void consumeFuel() {
        int currentLevel = getFuelLevel();
        if (currentLevel == 0) return;

        editor.putInt(FUEL_LEVEL, currentLevel - 1);
        editor.commit();
    }
}

package com.mirkoddd.charon.sample.game;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Mirko Dimartino on 25/06/26.
 */
public class VehicleManager {
    private static final String PREF_NAME = "vehicle_manager";
    private static final String CURRENT_VEHICLE = "current_vehicle";

    private final SharedPreferences.Editor editor;
    private final SharedPreferences prefs;

    public VehicleManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void setVehicleType(VehicleType type){
        editor.putString(CURRENT_VEHICLE, type.name());
        editor.commit();
    }

    public VehicleType getCurrentVehicleType(){
        String vehicleName = prefs.getString(CURRENT_VEHICLE, VehicleType.TYPE_DEFAULT.name());
        return VehicleType.valueOf(vehicleName);
    }
}

package com.mirkoddd.charon.sample;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.mirkoddd.charon.Charon;
import com.mirkoddd.charon.CharonError;
import com.mirkoddd.charon.catalog.CharonCatalog;
import com.mirkoddd.charon.catalog.CharonOffer;
import com.mirkoddd.charon.inventory.CharonInventory;
import com.mirkoddd.charon.inventory.CharonPurchase;
import com.mirkoddd.charon.sample.config.AppCatalog;
import com.mirkoddd.charon.sample.databinding.ActivityDriveBinding;
import com.mirkoddd.charon.sample.game.VehicleManager;
import com.mirkoddd.charon.sample.game.VehicleType;

/**
 * Created by Mirko Dimartino on 09/06/26.
 */
public class DriveActivity extends AppCompatActivity {

    private ActivityDriveBinding binding;

    private CharonOffer refillGasOffer;
    private CharonOffer buyMotoOffer;
    private boolean hasBoughtMotoAlready;
    private VehicleManager vehicleManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        vehicleManager = new VehicleManager(this);

        binding.defaultMoto.setEnabled(false);
        binding.premiumMoto.setEnabled(false);

        binding.defaultMoto.setOnClickListener(v -> selectDefaultMoto());
        binding.premiumMoto.setOnClickListener(v -> selectPremiumMoto());


        binding.driveView.setOnDriveListener(() -> {
            updateButtons();

            String text = binding.driveView.isTargetReached() ? "Restart" : "Drive";
            binding.drive.setText(text);
        });

        binding.drive.setOnClickListener(v -> binding.driveView.advance());


        binding.refillGas.setOnClickListener(v -> buyRefill());

        updateButtons();


        Charon.getInstance().observeCheckout().events().observe(this,
                checkout -> checkout.handle(
                        success -> checkoutDone(success.purchase()),
                        failed -> checkoutFailed(failed.error()),
                        validating -> checkoutValidating(validating.purchase()),
                        pending -> checkoutPending(pending.purchase()),
                        () -> { /*TODO*/}
                ));

        Charon.getInstance().observeState().connectionState().observe(this, state -> {
            updateCatalog(state.catalog());
            updateInventory(state.inventory());
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVehicleType();
    }

    private void updateVehicleType(){
        VehicleType type = vehicleManager.getCurrentVehicleType();
        Log.e("VEHICLE", "updateVehicleType: " + type.name() );
        if (type == VehicleType.TYPE_DEFAULT) selectDefaultMoto();
        else selectPremiumMoto();
    }


    private void updateButtons() {
        binding.drive.setEnabled(!binding.driveView.isOutOfFuel());
        binding.refillGas.setEnabled(binding.driveView.isOutOfFuel());
    }

    private void updateInventory(CharonInventory inventory) {
        for (var pending : inventory.pendingPurchases()) {
            boolean pendingFuel = pending.skus().contains(AppCatalog.REFILL_TANK.id());
            if (pendingFuel) binding.status.setText("Refill pending");
        }

        hasBoughtMotoAlready = inventory.activeSkus().contains(AppCatalog.BUY_MOTO.id());
        binding.defaultMoto.setEnabled(true);
        binding.premiumMoto.setEnabled(true);
        if (hasBoughtMotoAlready){
            binding.motoPrice.setText("Owned");
        } else {
            if (vehicleManager.getCurrentVehicleType() == VehicleType.TYPE_PREMIUM){
                selectDefaultMoto();
            }
        }
        updateVehicleType();
    }

    private void updateCatalog(CharonCatalog catalog) {
        refillGasOffer = catalog
                .findOffer(AppCatalog.REFILL_TANK)
                .orElseThrow(); // I don't mind throwing an exception here, it's just a demo

        buyMotoOffer = catalog
                .findOffer(AppCatalog.BUY_MOTO)
                .orElseThrow(); // I don't mind throwing an exception here either

        String motoPrice = buyMotoOffer.pricingInfo().currentPrice();
        binding.motoPrice.setText(motoPrice);
    }

    private void checkoutValidating(CharonPurchase purchase) {
        if (purchase == null) return;

        binding.refillGas.setEnabled(false);
        binding.status.setText("Validating = " + purchase.skus());
    }

    private void checkoutFailed(CharonError error) {
        binding.status.setText("Refill failed = " + error.message());
    }

    private void checkoutDone(CharonPurchase purchase) {
        if (purchase.skus().contains("refill_gas")) {
            refill();
            binding.status.setText("Refill done");
        }

        if (purchase.skus().contains("buy_motorcycle")) {
            selectPremiumMoto();
            binding.status.setText("Moto purchased");
        }
    }

    private void checkoutPending(CharonPurchase purchase) {
        if (purchase.skus().contains("refill_gas")) {
            binding.status.setText("Refill pending");
        }
    }


    private void buyMoto(){
        Charon.getInstance().buy()
                .item(buyMotoOffer)
                .launch(this);
    }

    private void buyRefill() {
        Charon.getInstance().buy()
                .item(refillGasOffer)
                .launch(this);
    }

    private void refill() {
        binding.driveView.refill();
        updateButtons();
    }

    private void selectDefaultMoto(){
        binding.defaultMoto.setChecked(true);
        binding.premiumMoto.setChecked(false);
        vehicleManager.setVehicleType(VehicleType.TYPE_DEFAULT);
        binding.driveView.setVehicleDefault();

    }
    private void selectPremiumMoto(){
        if (!hasBoughtMotoAlready){
            buyMoto();
            return;
        }
        binding.defaultMoto.setChecked(false);
        binding.premiumMoto.setChecked(true);
        vehicleManager.setVehicleType(VehicleType.TYPE_PREMIUM);
        binding.driveView.setVehicleMoto();
    }



}

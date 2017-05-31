package com.aquosense.proteos;

import android.AndroidBluetoothLeBridge;
import android.app.Application;
import android.content.pm.PackageManager;
import android.widget.Toast;

import interfaces.BleLinkCompatApplication;
import interfaces.ILinkBridge;

/**
 * Created by francis on 11/4/16.
 */
public class ProteosApp extends Application implements BleLinkCompatApplication {
    private static ILinkBridge _bluetoothbridge = null;

    @Override
    public void onCreate() {
        super.onCreate();

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Feature check successful", Toast.LENGTH_SHORT).show();
            _bluetoothbridge = AndroidBluetoothLeBridge.getInstance();
        } else {
            Toast.makeText(this, "Feature check failed", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "Sorry, your phone does not support Bluetooth LE",
                    Toast.LENGTH_LONG).show();
        }

        return;
    }

    @Override
    public ILinkBridge getBluetoothBridge() {
        return _bluetoothbridge;
    }
}

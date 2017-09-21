package com.aquosense.proteos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSetup = (Button) findViewById(R.id.btn_setup);
        btnSetup.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ChooseDeviceActivity.class);
                        startActivity(intent);
                    }
                }
        );

        Button btnRead = (Button) findViewById(R.id.btn_read);
        btnRead.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ReadActivity.class);
                        startActivity(intent);
                    }
                }
        );

        Button btn_calib = (Button) findViewById(R.id.btn_calib);
        btn_calib.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, CalibrateActivity.class);
                        startActivity(intent);
                    }
                }
        );

        SharedPreferences userSettings = getSharedPreferences("APP_SETTINGS", Context.MODE_PRIVATE);
        String boundDeviceName = userSettings.getString("DEVICE_NAME", "");
        String boundDeviceAddr = userSettings.getString("DEVICE_ADDR", "");

        TextView txvBoundDevice = (TextView) findViewById(R.id.txv_bound_device);
        if (boundDeviceAddr.equals("")) {
            txvBoundDevice.setText("No device bound");
            txvBoundDevice.setTextColor(0xFFFF2222);
        } else {
            txvBoundDevice.setText("Target Device: " + boundDeviceName + " (" + boundDeviceAddr  + ")");
            txvBoundDevice.setTextColor(0xFF229922);
        }
    }
}

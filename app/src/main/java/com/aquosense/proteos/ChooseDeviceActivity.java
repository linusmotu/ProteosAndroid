package com.aquosense.proteos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;

import com.aquosense.proteos.types.BleDeviceListAdapter;
import com.aquosense.proteos.types.ServiceBindingActivity;

import java.util.ArrayList;
import java.util.List;

import types.FoundDevice;
import types.RetStatus;

public class ChooseDeviceActivity extends ServiceBindingActivity {
    private List<FoundDevice> _dvcList = null;
    private BleDeviceListAdapter _dvcListAdapter = null;
    private boolean _bIsReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_device);

        _dvcList = new ArrayList<>();
        _dvcListAdapter = new BleDeviceListAdapter(this, _dvcList);

        ListView lsvDevices = (ListView) findViewById(R.id.list_devices);
        lsvDevices.setAdapter(_dvcListAdapter);

        lsvDevices.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        FoundDevice fd = _dvcListAdapter.getItem(position);
                        if (fd == null) {
                            return;
                        }

                        SharedPreferences userSettings =
                                ChooseDeviceActivity.this
                                    .getSharedPreferences("APP_SETTINGS", Context.MODE_PRIVATE);

                        /* Save the chosen device to shared prefs */
                        userSettings.edit()
                                .putString("DEVICE_NAME", fd.getName())
                                .putString("DEVICE_ADDR", fd.getAddress())
                                .commit();

                        display("Device Selected: " + fd.getName() + " (" + fd.getAddress() + ")");

                        finish();

                        return;
                    }
                }
        );

        ImageButton btnScan = (ImageButton) findViewById(R.id.btn_dvc_scan);
        btnScan.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (callService(BleLinkService.MSG_START) != RetStatus.OK) {
                            display("Service Initialize Failed");
                        }

                        if (callService(BleLinkService.MSG_START_DISCOVER) != RetStatus.OK) {
                            display("Service Device Discovery Failed");
                        } else {
                            display("Device Discovery Started");
                        }

                        return;
                    }
                }
        );

        return;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!_bIsReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BleLinkService.ACTION_UPDATE_FOUND);
            intentFilter.addAction(BleLinkService.ACTION_UPDATE_LOST);
            registerReceiver(_receiver, intentFilter);

            _bIsReceiverRegistered = true;
        }

        return;
    }

    @Override
    protected void onStop() {
        if (callService(BleLinkService.MSG_STOP_DISCOVER) != RetStatus.OK) {
            display("Stop Device Discovery Failed");
        }

        if (_bIsReceiverRegistered) {
            unregisterReceiver(_receiver);
            _bIsReceiverRegistered = false;
        }

        super.onStop();

        return;
    }

    @Override
    protected void onDestroy() {
        if (callService(BleLinkService.MSG_STOP) != RetStatus.OK) {
            display("Stop Service Failed");
        }

        super.onDestroy();
        return;
    }

    private final BroadcastReceiver _receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BleLinkService.ACTION_UPDATE_FOUND.equals(action)) {
                String deviceName = intent.getStringExtra("NAME");
                String deviceAddr = intent.getStringExtra("ADDRESS");
                short deviceRssi = intent.getShortExtra("RSSI", (short) (0));

                if (deviceName == null) {
                    deviceName = "???";
                }

                if (deviceAddr == null) {
                    return;
                }

                /* Check if we already have the device in our list */
                for (FoundDevice fd : _dvcList) {
                    if (fd.getAddress().equals(deviceAddr)) {
                        if (fd.getName() != deviceName) {
                            display("Updated Device Entry: " + deviceName + " (" + deviceAddr + ")");
                        }
                        fd.setName(deviceName);
                        _dvcListAdapter.notifyDataSetChanged();
                        return;
                    }
                }

                /* If not, create a new entry for it */
                _dvcList.add(new FoundDevice(deviceName, deviceAddr));
                _dvcListAdapter.notifyDataSetChanged();
                display("New Device Found: " + deviceName + " (" + deviceAddr + ")");

            } else if (BleLinkService.ACTION_UPDATE_LOST.equals(action)) {
                String deviceAddr = intent.getStringExtra("ADDRESS");

                if (deviceAddr == null) {
                    return;
                }

                for (FoundDevice b : _dvcList) {
                    if (b.getAddress().equals(deviceAddr)) {
                        display("Device lost: " + b.getName());
                        _dvcList.remove(b);
                        _dvcListAdapter.notifyDataSetChanged();
                        return;
                    }
                }
            }

            return;
        }
    };
}

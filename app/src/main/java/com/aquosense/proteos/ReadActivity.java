package com.aquosense.proteos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.aquosense.proteos.types.ServiceBindingActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import types.RetStatus;
import utils.Logger;

public class ReadActivity extends ServiceBindingActivity {
    private boolean _bIsReceiverRegistered = false;
    private boolean _bIsReadTriggered = false;
    private boolean _bIsConnected = false;

    private String _deviceAddress = "";
    private HashMap<String, String> _sensorValues = new HashMap<>();
    private int _iSensorsRead = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read);

        SharedPreferences userSettings = getSharedPreferences("APP_SETTINGS", Context.MODE_PRIVATE);
        _deviceAddress = userSettings.getString("DEVICE_ADDR", "");
        Logger.info("Device address = " + _deviceAddress);

        ImageButton btnRead = (ImageButton) findViewById(R.id.btn_read);
        btnRead.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!_bIsConnected) {
                            display("Not yet connected");
                        } else if (_iSensorsRead < 8) {
                            display("Awaiting results from " + (4 - _iSensorsRead) + " sensors");
                        } else {
                            new ReadDataTask().execute(_deviceAddress);
                        }
                    }
                }
        );

        /* Initialize the sensor values map */
        _sensorValues.put("PH", "0.0");
        _sensorValues.put("DO2", "0.0");
        _sensorValues.put("CONDUCTIVITY", "0.0");
        _sensorValues.put("TEMPERATURE", "0.0");
        _sensorValues.put("AMMONIUM", "0.0");
        _sensorValues.put("AMMONIUM2", "0.0");
        _sensorValues.put("TOTALDISSOLVEDSOLID", "0.0");
        _sensorValues.put("SALINITY", "0.0");
        _sensorValues.put("SPECIFICGRAVITY", "0.0");
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!_bIsReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BleLinkService.ACTION_CONNECTED);
            intentFilter.addAction(BleLinkService.ACTION_DISCONNECTED);
            intentFilter.addAction(BleLinkService.ACTION_RECV_PH);
            intentFilter.addAction(BleLinkService.ACTION_RECV_DO);
            intentFilter.addAction(BleLinkService.ACTION_RECV_EC);
            intentFilter.addAction(BleLinkService.ACTION_RECV_TM);
            intentFilter.addAction(BleLinkService.ACTION_RECV_AM);
            intentFilter.addAction(BleLinkService.ACTION_RECV_AM2);
            intentFilter.addAction(BleLinkService.ACTION_RECV_TDS);
            intentFilter.addAction(BleLinkService.ACTION_RECV_SAL);
            intentFilter.addAction(BleLinkService.ACTION_RECV_SG);

            registerReceiver(_receiver, intentFilter);
            _bIsReceiverRegistered = true;
        }

        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if (_deviceAddress == "") {
                            return;
                        }

                        RetStatus status;
                        status = callService(BleLinkService.MSG_START);
                        if (status != RetStatus.OK) {
                            display("Service initialize failed");
                        }

                        Bundle extras = new Bundle();
                        extras.putString("DEVICE_ADDR", _deviceAddress);
                        status = callService(BleLinkService.MSG_CONNECT, extras, null);
                        if (status != RetStatus.OK) {
                            display("Connect failed");
                        }
                    }
                }, 1000
        );

        return;
    }

    @Override
    protected void onStop() {
        if (callService(BleLinkService.MSG_DISCONNECT) != RetStatus.OK) {
            display("Disconnect Failed");
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("IS_CONNECTED", _bIsConnected);
        outState.putString("VALUE_PH", _sensorValues.get("PH"));
        outState.putString("VALUE_DO", _sensorValues.get("DO2"));
        outState.putString("VALUE_EC", _sensorValues.get("CONDUCTIVITY"));
        outState.putString("VALUE_TM", _sensorValues.get("TEMPERATURE"));
        outState.putString("VALUE_AM", _sensorValues.get("AMMONIUM"));
        outState.putString("VALUE_AM2", _sensorValues.get("AMMONIUM2"));
        outState.putString("VALUE_TDS", _sensorValues.get("TOTALDISSOLVEDSOLID"));
        outState.putString("VALUE_SAL", _sensorValues.get("SALINITY"));
        outState.putString("VALUE_SG", _sensorValues.get("SPECIFICGRAVITY"));

        super.onSaveInstanceState(outState);
        return;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean("IS_CONNECTED")) {
            TextView txvConnStatus = (TextView) findViewById(R.id.txv_conn_status);
            txvConnStatus.setText("Status: Connected");
            _bIsConnected = true;
        }

        String val;

        val = savedInstanceState.getString("VALUE_PH");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_ph);
            txvSensor.setText(val);
            _sensorValues.put("PH", val);
        }

        val = savedInstanceState.getString("VALUE_DO");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_do);
            txvSensor.setText(val);
            _sensorValues.put("DO2", val);
        }

        val = savedInstanceState.getString("VALUE_EC");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_ec);
            txvSensor.setText(val);
            _sensorValues.put("CONDUCTIVITY", val);
        }

        val = savedInstanceState.getString("VALUE_TM");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_temp);
            txvSensor.setText(val);
            _sensorValues.put("TEMPERATURE", val);
        }

        val = savedInstanceState.getString("VALUE_AM");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_amm);
            txvSensor.setText(val);
            _sensorValues.put("AMMONIUM", val);
        }

        val = savedInstanceState.getString("VALUE_AM2");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_amm2);
            txvSensor.setText(val);
            _sensorValues.put("AMMONIUM2", val);
        }

        val = savedInstanceState.getString("VALUE_TDS");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_tds);
            txvSensor.setText(val);
            _sensorValues.put("TOTALDISSOLVEDSOLID", val);
        }

        val = savedInstanceState.getString("VALUE_SAL");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_sal);
            txvSensor.setText(val);
            _sensorValues.put("SALINITY", val);
        }

        val = savedInstanceState.getString("VALUE_SG");
        if (val != null) {
            TextView txvSensor = (TextView) findViewById(R.id.txv_sg);
            txvSensor.setText(val);
            _sensorValues.put("SPECIFICGRAVITY", val);
        }

        return;
    }

    public void logandupload(String data, boolean append){
        String directoryName = "aquosense";
        String fileName = "receivedData_" + getDateToday() + ".txt";

        File dir = new File(android.os.Environment.getExternalStorageDirectory(), directoryName);
        if(!dir.exists()) {
            dir.mkdirs();
        }

        File f = new File(dir + File.separator + fileName);
        try {
            FileOutputStream fOut = new FileOutputStream(f, append);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.write(data + "\n");
            myOutWriter.close();
            fOut.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private String getDateToday() {
        DateFormat dfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        return dfDate.format(Calendar.getInstance().getTime());
    }

    private class ReadDataTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String deviceAddr = params[0];

            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                /* Fall through */
            }

            if (callService(BleLinkService.MSG_READ_ALL) != RetStatus.OK) {
                display("Read data failed");
            }

            _bIsReadTriggered = true;
            _iSensorsRead = 0;

            return null;
        }

    }

    private final BroadcastReceiver _receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BleLinkService.ACTION_CONNECTED.equals(action)) {
                TextView txvConnStatus = (TextView) findViewById(R.id.txv_conn_status);
                txvConnStatus.setText("Status: Connected");
                _bIsConnected = true;

                new ReadDataTask().execute(_deviceAddress);

            } else if (BleLinkService.ACTION_DISCONNECTED.equals(action)) {
                TextView txvConnStatus = (TextView) findViewById(R.id.txv_conn_status);
                txvConnStatus.setText("Status: Disconnected");
                _bIsConnected = false;

            } else if (BleLinkService.ACTION_RECV_PH.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_ph);
                txvSensor.setText(val);
                _sensorValues.put("PH", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_DO.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_do);
                txvSensor.setText(val);
                _sensorValues.put("DO2", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_EC.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_ec);
                txvSensor.setText(val);
                _sensorValues.put("CONDUCTIVITY", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_TM.equals(action)) {
                String val = intent.getStringExtra("VALUE");

                TextView txvSensor = (TextView) findViewById(R.id.txv_temp);
                txvSensor.setText(val);
                _sensorValues.put("TEMPERATURE", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_AM.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_amm);
                txvSensor.setText(val);
                _sensorValues.put("AMMONIUM", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_AM2.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_amm2);
                txvSensor.setText(val);
                _sensorValues.put("AMMONIUM2", val);

                _iSensorsRead++;
            } else if (BleLinkService.ACTION_RECV_TDS.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_tds);
                txvSensor.setText(val);
                _sensorValues.put("TOTALDISSOLVEDSOLID", val);

                _iSensorsRead++;

            } else if (BleLinkService.ACTION_RECV_SAL.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_sal);
                txvSensor.setText(val);
                _sensorValues.put("SALINITY", val);

                _iSensorsRead++;
            } else if (BleLinkService.ACTION_RECV_SG.equals(action)) {
                String val = intent.getStringExtra("VALUE");
                logandupload(val, true);
                TextView txvSensor = (TextView) findViewById(R.id.txv_sg);
                txvSensor.setText(val);
                _sensorValues.put("SPECIFICGRAVITY", val);

                _iSensorsRead++;
            }

            if (_iSensorsRead >= 8) {
                _bIsReadTriggered = false;
            }

            return;
        }
    };
}

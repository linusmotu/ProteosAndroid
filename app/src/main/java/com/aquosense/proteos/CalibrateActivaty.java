package com.aquosense.proteos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.aquosense.proteos.types.ServiceBindingActivity;

import java.util.HashMap;

import types.RetStatus;

public class CalibrateActivaty extends ServiceBindingActivity implements View.OnClickListener {
    private boolean _bIsReceiverRegistered = false;
    private boolean _bIsReadTriggered = false;
    private int _iSensorsRead = 8;
    private Button btnFixedPh;
    private Button btnFixedDO;
    private Button btnFixedEC;
    private Button btnVariablePH;
    private Button btnVariableDO;
    private Button btnVariableEC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate);

        btnFixedPh = (Button) findViewById(R.id.fixedPH);
        btnFixedDO = (Button) findViewById(R.id.fixedDO);
        btnFixedEC = (Button) findViewById(R.id.fixedEC);
        btnVariablePH = (Button) findViewById(R.id.variablePH);
        btnVariableDO = (Button) findViewById(R.id.variableDO);
        btnVariableEC = (Button) findViewById(R.id.variableEC);

        btnFixedPh.setOnClickListener(this);
        btnFixedDO.setOnClickListener(this);
        btnFixedEC.setOnClickListener(this);
        btnVariablePH.setOnClickListener(this);
        btnVariableDO.setOnClickListener(this);
        btnVariableEC.setOnClickListener(this);
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
    }

    @Override
    protected void onDestroy() {
        if (callService(BleLinkService.MSG_STOP) != RetStatus.OK) {
            display("Stop Service Failed");
        }
        super.onDestroy();
    }

    @Override
    protected void display(String msg) {
        super.display(msg);
    }

    @Override
    protected RetStatus callService(int msgId) {
        return super.callService(msgId);
    }

    @Override
    protected RetStatus callService(int msgId, Bundle extras) {
        return super.callService(msgId, extras);
    }

    private class ReadDataTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
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

//            if (BleLinkService.ACTION_CONNECTED.equals(action)) {
//                TextView txvConnStatus = (TextView) findViewById(R.id.txv_conn_status);
//                txvConnStatus.setText("Status: Connected");
//                _bIsConnected = true;
//
//                new ReadActivity.ReadDataTask().execute(_deviceAddress);
//
//            } else if (BleLinkService.ACTION_DISCONNECTED.equals(action)) {
//                TextView txvConnStatus = (TextView) findViewById(R.id.txv_conn_status);
//                txvConnStatus.setText("Status: Disconnected");
//                _bIsConnected = false;
//
//            } else if (BleLinkService.ACTION_RECV_CAL.equals(action)) {
//                String val = intent.getStringExtra("VALUE");
//
//                TextView txvSensor = (TextView) findViewById(R.id.txv_ph);
//                txvSensor.setText(val);
//                _sensorValues.put("PH", val);
//
//                _iSensorsRead++;
//
//            }
//
//            if (_iSensorsRead >= 8) {
//                _bIsReadTriggered = false;
//            }
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.fixedPH:
                break;
            case R.id.fixedDO:
                break;
            case R.id.fixedEC:
                break;
            case R.id.variablePH:
                break;
            case R.id.variableDO:
                break;
            case R.id.variableEC:
                break;
            default:
                break;
        }
    }
}

package com.aquosense.proteos.types;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.aquosense.proteos.BleLinkService;
import com.aquosense.proteos.ProteosApp;

import types.RetStatus;
import utils.Logger;

/**
 * Created by francis on 2/20/16.
 */
public abstract class ServiceBindingActivity extends AppCompatActivity {
    private Messenger _messenger    = null;
    private Messenger _service      = null;
    private boolean   _bIsBound     = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		/* Bind to the service if not yet bound */
        if (!_bIsBound) {
            Intent bindServiceIntent = new Intent(this, BleLinkService.class);
            bindService(bindServiceIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
		/* Unbind from our service */
        if (_bIsBound) {
            unbindService(_serviceConnection);
            _bIsBound = false;
        }

        super.onDestroy();
    }

    /* ******************* */
    /* Protected Functions */
    /* ******************* */
    protected void display(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Logger.info(msg);
    }

    protected RetStatus callService(int msgId) {
        return callService(msgId, null);
    }

    protected RetStatus callService(int msgId, Bundle extras) {
        Logger.info("Calling BleLinkService with MsgId=" + msgId);

        if (_service == null) {
            Logger.err("callService(): Service unavailable");
            return RetStatus.FAILED;
        }

        if (!_bIsBound) {
            Logger.err("callService(): Service unavailable");
            return RetStatus.FAILED;
        }

        Message msg = Message.obtain(null, msgId, 0, 0);
        msg.replyTo = null;
        msg.setData(extras);

        try {
            _service.send(msg);
        } catch (Exception e) {
            Logger.err("callService(): Failed to call service: " + e.getMessage());
            return RetStatus.FAILED;
        }

        return RetStatus.OK;
    }

    /* ********************* */
    /* Private Inner Classes */
    /* ********************* */
    private ServiceConnection _serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName className) {
            _service = null;
            _bIsBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            _service = new Messenger(binder);
            _bIsBound = true;
        }
    };
}

package interfaces;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

import java.util.concurrent.locks.ReentrantLock;

import types.LinkState;
import types.ReceivedData;
import types.RetStatus;
import types.ServiceState;
import utils.Constants;
import utils.Logger;

/**
 * Created by francis on 5/18/16.
 */
public abstract class BleLinkBaseService extends Service implements LinkEventHandler {
    protected static final String SERVICE_ACTION 	    = "com.aquosense.blelinklib.action.";
    public static final String ACTION_CONNECTED 	    = SERVICE_ACTION + "CONNECTED";
    public static final String ACTION_DATA_RECEIVE	    = SERVICE_ACTION + "DATA_RECEIVED";
    public static final String ACTION_UPDATE_LOST 	    = SERVICE_ACTION + "UPDATE_LOST";
    public static final String ACTION_UPDATE_FOUND 	    = SERVICE_ACTION + "UPDATE_FOUND";
    public static final String ACTION_DISCONNECTED 	    = SERVICE_ACTION + "DISCONNECTED";
    public static final String ACTION_STATE_CHANGED	    = SERVICE_ACTION + "STATE_CHANGED";

    private ServiceState _eState = ServiceState.UNKNOWN;
    protected ILinkBridge _linkBridge = null;
    protected ReentrantLock _tStateLock = new ReentrantLock();
    private final Messenger _messenger = new Messenger(new BaseMessageHandler());

    @Override
    public IBinder onBind(Intent intent) {
        display("Service bound");
        return _messenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        /* Obtain our link bridge from the bound Application */
        _linkBridge = this.getLinkBridge(); //_app.getBluetoothBridge();
        if (_linkBridge == null) {
            Logger.err("Link bridge has not been set");
            stopSelf();
            return;
        }

		/* Set this service as an event handler for Bluetooth events */
        _linkBridge.setEventHandler(this);

        return;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        return;
    }

    @Override
    public void onConnected(String name, String address) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_CONNECTED);
        intent.putExtra("SENDER_NAME", name);
        intent.putExtra("SENDER_ADDR", address);
        sendBroadcast(intent);

        return;
    }

    @Override
    public void onDisconnected(String name, String address) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_DISCONNECTED);
        intent.putExtra("SENDER_NAME", name);
        intent.putExtra("SENDER_ADDR", address);
        sendBroadcast(intent);

        return;
    }

    @Override
    public void onDeactivated() {
		/* TODO */
        return;
    }

    @Override
    public void onDataReceived(String name, String address, byte[] data) {
        Logger.info("onDataReceived() invoked");
        if (address == null) {
            Logger.warn("Received data has an invalid sender");
            return;
        }

        if (address.isEmpty()) {
            Logger.warn("Received data has an invalid sender");
            return;
        }

        if (data == null) {
            Logger.warn("Received data is invalid");
            return;
        }

        if (data.length == 0) {
            Logger.warn("Received data is empty");
            return;
        }

        if (data.length > 1024) {
			/* This is also a case for DISCONNECTION since this is a known
			 *  buffer overloading failure case on some devices */
            Logger.warn("Received data overloads our buffers");
            return;
        }

		/* Start a new AsyncTask to handle it */
        new HandleReceivedDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                new ReceivedData(name, address, data));

        return;
    }

    protected abstract ILinkBridge getLinkBridge();
    protected abstract boolean isReceivedDataValid(ReceivedData data);
    protected abstract RetStatus handleReceivedData(HandleReceivedDataTask task,
                                                    String sender, String address, byte data[]);
    protected abstract RetStatus handleServiceMessage(Handler h, Message msg);
    protected abstract RetStatus start();
    protected abstract RetStatus stop();

    /** Private Methods **/
    protected synchronized ServiceState getState() {
        ServiceState state;
        _tStateLock.lock();
        state = _eState;
        _tStateLock.unlock();
        return state;
    }

    protected synchronized void setState(ServiceState state) {
        _tStateLock.lock();
        _eState = state;
        _tStateLock.unlock();
        Logger.info("State set to " + _eState.toString());

        broadcastServiceStateChanged();
        return;
    }

    protected BleLinkCompatApplication getAppRef() {
        return (BleLinkCompatApplication) getApplication();
    }

    protected LinkState getLinkState() {
        if (_linkBridge == null) {
            _linkBridge = getLinkBridge();
        }
        return _linkBridge.getState();
    }

    protected void display(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Logger.info(msg);
        return;
    }

    protected RetStatus replyLinkState(Messenger replyTo) {
        Bundle data = new Bundle();
        data.putString("STATE", getLinkState().toString());

        Message msg = Message.obtain(null, Constants.MSG_RESP_LINK_STATE, 0, 0);
        msg.setData(data);

        try {
            replyTo.send(msg);
        } catch (Exception e) {
            Logger.err("Exception occurred: " + e.getMessage());
        }
        return RetStatus.OK;
    }

    protected RetStatus replyServiceState(Messenger replyTo) {
        Bundle data = new Bundle();
        data.putString("STATE", getState().toString());

        Message msg = Message.obtain(null, Constants.MSG_RESP_SERVICE_STATE, 0, 0);
        msg.setData(data);

        try {
            replyTo.send(msg);
        } catch (Exception e) {
            Logger.err("Exception occurred: " + e.getMessage());
        }
        return RetStatus.OK;
    }

    protected void broadcastServiceStateChanged() {
		/* Broadcast our received data for our receivers */
        Intent foundIntent = new Intent(ACTION_STATE_CHANGED);
        foundIntent.putExtra("STATE", getState().toString());
        sendBroadcast(foundIntent);
        return;
    }

    /** Protected Inner Classes **/
    protected class BaseMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (handleServiceMessage(this, msg) != RetStatus.OK) {
                Logger.err("Failed to handle message!");
            }
            return;
        }
    }

    protected class HandleReceivedDataTask extends
            AsyncTask<ReceivedData, String, Void> {

        @Override
        protected Void doInBackground(ReceivedData... params) {
//            publishProgress("Handle Received Data Task Started");

            ReceivedData recvData = params[0];
            if (!isReceivedDataValid(recvData)) {
                Logger.err("Received data is invalid");
                return null;
            }

			/* SAN-check that the received data is not null */
            if (recvData == null) {
                Logger.err("Received data is NULL");
                return null;
            }

            String sender = recvData.getSenderName();
            String address = recvData.getSenderAddress();
            byte data[] = recvData.getData();

//            /* Sidekick data will always consist of a 4-byte HDR and an N-byte payload,
//             *  so a data length of less than 4 is automatically invalid */
//            if (data.length < 4) {
//                Logger.err("Invalid data received: " + new String(data).trim());
//                return null;
//            }

            /* Invoke the handleReceivedData() function for processing */
            if ( handleReceivedData(this, sender, address, data) != RetStatus.OK ) {
                Logger.err("Failed to handle received data");
                return null;
            }

            return null;
        }

        public void displayMsg(String msg) {
            publishProgress(msg);
            return;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0] == null) {
                return;
            }

            display(values[0]);

            super.onProgressUpdate(values);
            return;
        }
    }


}

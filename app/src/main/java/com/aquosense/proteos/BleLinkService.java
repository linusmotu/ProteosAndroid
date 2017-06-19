package com.aquosense.proteos;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import interfaces.BleLinkBaseService;
import interfaces.ILinkBridge;
import types.FoundDevice;
import types.LinkState;
import types.ReceivedData;
import types.RetStatus;
import types.ServiceState;
import utils.Constants;
import utils.Logger;

/**
 * Created by francis on 11/4/16.
 */
public class BleLinkService extends BleLinkBaseService {
    public static final int MSG_START			    = 1;
    public static final int MSG_STOP 				= 2;
    public static final int MSG_START_DISCOVER 		= 3;
    public static final int MSG_STOP_DISCOVER 		= 4;
    public static final int MSG_CONNECT 			= 5;
    public static final int MSG_DISCONNECT 			= 6;
    public static final int MSG_QUERY_STATE 		= 7;
    public static final int MSG_QUERY_LINK_STATE 	= 8;
    public static final int MSG_READ_ALL 			= 9;

    public static String ACTION_RECV_TM = SERVICE_ACTION + "RECV_TM";
    public static String ACTION_RECV_PH = SERVICE_ACTION + "RECV_PH";
    public static String ACTION_RECV_EC = SERVICE_ACTION + "RECV_EC";
    public static String ACTION_RECV_DO = SERVICE_ACTION + "RECV_DO";
    public static String ACTION_RECV_AM = SERVICE_ACTION + "RECV_AM";

    public static final String REQ_READ_ALL = "read.start a";
    public static final String RXX_TERM = ";";

    private int _iSerialTxLen = Constants.MAX_TX_LEN;

    private List<FoundDevice> _foundDevices = new ArrayList<>();

    private ReentrantLock _tRequestLock = new ReentrantLock();

    @Override
    public void onCreate() {
        super.onCreate();

        /* Set up the receiver to listen for link events */
        if (_receiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Constants.ACTION_DISCOVERED);
            registerReceiver(_receiver, filter);
            Logger.info("[BleLinkService] Receiver registered");
        }
    }

    @Override
    public void onDestroy() {
        if (_receiver != null) {
            unregisterReceiver(_receiver);
            Logger.info("[BleLinkService] Receiver unregistered");
        }

        super.onDestroy();
    }

    @Override
    protected ILinkBridge getLinkBridge() {
        return getAppRef().getBluetoothBridge();
    }

    @Override
    protected boolean isReceivedDataValid(ReceivedData data) {
        return true;
    }

    @Override
    protected RetStatus handleReceivedData(HandleReceivedDataTask task, String sender, String address, byte[] data) {
        String dataStr = new String(data);

        if (dataStr.contains("pH: ")) {
            String dataPart[] = dataStr.trim().split(" ");
            if (dataPart.length == 2) {
                String val = dataPart[1];
                broadcastRecvdPH(val);
            }
        } else if (dataStr.contains("Temp: ")) {
            String dataPart[] = dataStr.trim().split(" ");
            if (dataPart.length == 2) {
                String val = dataPart[1];
                broadcastRecvdTM(val);
            }
        } else if (dataStr.contains("DO: ")) {
            String dataPart[] = dataStr.trim().split(" ");
            if (dataPart.length == 2) {
                String val = dataPart[1];
                broadcastRecvdDO(val);
            }
        } else if (dataStr.contains("EC: ")) {
            String dataPart[] = dataStr.trim().split(" ");
            if (dataPart.length == 2) {
                String val = dataPart[1];
                broadcastRecvdEC(val);
            }
        } else if (dataStr.contains("AMM: ")) {
            String dataPart[] = dataStr.trim().split(" ");
            if (dataPart.length == 2) {
                String val = dataPart[1];
                broadcastRecvdAM(val);
            }
        } else {
            Logger.warn("Unknown handling for received data from " +
                    sender + " (" + address + "): " + new String(data));
        }
        return RetStatus.OK;
    }

    @Override
    protected RetStatus handleServiceMessage(Handler h, Message msg) {
        RetStatus status = RetStatus.FAILED;
        Bundle data = msg.getData();
        switch (msg.what) {
            case MSG_START:
                status = start();
                break;
            case MSG_STOP:
                status = stop();
                break;
            case MSG_START_DISCOVER:
                status = startDiscovery();
                break;
            case MSG_STOP_DISCOVER:
                status = stopDiscovery();
                break;
            case MSG_CONNECT:
                if (data == null) {
                    Logger.err("Connect Failed: No device address provided");
                    break;
                }
                String connAddr = data.getString("DEVICE_ADDR", "");
                status = connect(connAddr);
                break;
            case MSG_DISCONNECT:
                if (data == null) {
                    Logger.err("Disconnect Failed: No device address provided");
                    break;
                }
                String discAddr = data.getString("DEVICE_ADDR", "");
                status = disconnect(discAddr);
                break;
            case MSG_QUERY_STATE:
                status = replyServiceState(msg.replyTo);
                break;
            case MSG_QUERY_LINK_STATE:
                status = replyLinkState(msg.replyTo);
                break;
            case MSG_READ_ALL:
                status = sendReadAllRequest();
            default:
                Logger.warn("Unknown service message: " + msg.what);
                status = RetStatus.OK;
                break;
        }

        return status;
    }

    @Override
    protected RetStatus start() {
        setState(ServiceState.DISCONNECTED);
        return RetStatus.OK;
    }

    protected RetStatus startDiscovery() {
        ILinkBridge linkBridge = getLinkBridge();
        if (linkBridge == null) {
            return RetStatus.FAILED;
        }

        /* Attempt to initialize the link bridge if it hasn't
         *  been initialized yet */
        if (linkBridge.getState() == LinkState.UNKNOWN) {
            if (linkBridge.initialize(this, false) != RetStatus.OK) {
                return RetStatus.FAILED;
            }
        }

        /* Initiate Sidekick discovery */
        linkBridge.startDiscovery();

        return RetStatus.OK;
    }

    // Stop Discovery is overriden by SidekickAdvertService
    protected RetStatus stopDiscovery() {
        ILinkBridge linkBridge = getLinkBridge();
        if (linkBridge == null) {
            return RetStatus.FAILED;
        }

        /* Attempt to initialize the link bridge if it hasn't
         *  been initialized yet */
        if (linkBridge.getState() == LinkState.UNKNOWN) {
            if (linkBridge.initialize(this, false) != RetStatus.OK) {
                Logger.err("Failed to initialize Sidekick Link Bridge");
                return RetStatus.FAILED;
            }
        }

        /* Halt Sidekick discovery */
        linkBridge.stopDiscovery();

        return RetStatus.OK;
    }

    protected RetStatus connect(String address) {
        _tRequestLock.lock();
        ILinkBridge linkBridge = getLinkBridge();

        RetStatus status;
        /* Initialize the link bridge if we haven't done so yet */
        status = linkBridge.initialize(this, false);
        if (status != RetStatus.OK) {
            Logger.err("Failed to initialize Sidekick Link Bridge");
            _tRequestLock.unlock();
            return RetStatus.FAILED;
        }
        linkBridge.setEventHandler(this);

        /* Attempt to connect to the Sidekick described by this address */
        status = linkBridge.connectDeviceByAddress(address);
        if (status != RetStatus.OK) {
            Logger.err("Failed to connect to device: " + address);
            _tRequestLock.unlock();
            return RetStatus.FAILED;
        }

        _tRequestLock.unlock();
        return RetStatus.OK;
    }

    protected RetStatus disconnect(String address) {
		/* Set state to UNKNOWN */
        setState(ServiceState.UNKNOWN);

		/* Halt ongoing Sidekick discovery operations if any */
        this.stopDiscovery();

        ILinkBridge linkBridge = getLinkBridge();
//		/* Broadcast a DISCONNECT signal if needed */
//        if ((linkBridge.getState() != LinkState.DISCONNECTED) &&
//                (linkBridge.getState() != LinkState.UNKNOWN)) {
//            linkBridge.broadcast(REQ_PREF_DISCONNECT.getBytes());
//        }
//
//		/* Sleep for a bit before actually disconnecting */
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//			/* Interruptions are fine */
//        }

		/* Disconnect this Sidekick in particular */
        return linkBridge.disconnectDeviceByAddress(address);
    }


    @Override
    protected RetStatus stop() {
        ILinkBridge linkBridge = getLinkBridge();
        if (linkBridge == null) {
            return RetStatus.FAILED;
        }

        if (linkBridge.destroy() != RetStatus.OK) {
            return  RetStatus.FAILED;
        }

        setState(ServiceState.DISCONNECTED);
        return RetStatus.OK;
    }

    /** Broadcast Methods **/
    protected void broadcastRecvdTM(String value) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_RECV_TM);
        intent.putExtra("VALUE", value);
        sendBroadcast(intent);
        return;
    }

    protected void broadcastRecvdPH(String value) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_RECV_PH);
        intent.putExtra("VALUE", value);
        sendBroadcast(intent);
        return;
    }

    protected void broadcastRecvdDO(String value) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_RECV_DO);
        intent.putExtra("VALUE", value);
        sendBroadcast(intent);
        return;
    }

    protected void broadcastRecvdEC(String value) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_RECV_EC);
        intent.putExtra("VALUE", value);
        sendBroadcast(intent);
        return;
    }

    protected void broadcastRecvdAM(String value) {
		/* Broadcast our received data for our receivers */
        Intent intent = new Intent(ACTION_RECV_AM);
        intent.putExtra("VALUE", value);
        sendBroadcast(intent);
        return;
    }

    protected void broadcastDeviceFound(FoundDevice device) {
		/* Broadcast our received data for our receivers */
        Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
        foundIntent.putExtra("NAME", device.getName());
        foundIntent.putExtra("ADDRESS", device.getAddress());
        foundIntent.putExtra("LOST_STATUS", false);
        foundIntent.putExtra("RSSI", device.getRssi());
        sendBroadcast(foundIntent);
        return;
    }

    protected void broadcastDeviceLost(FoundDevice device) {
		/* Broadcast our received data for our receivers */
        Intent foundIntent = new Intent(ACTION_UPDATE_LOST);
        foundIntent.putExtra("NAME", device.getName());
        foundIntent.putExtra("ADDRESS", device.getAddress());
        foundIntent.putExtra("LOST_STATUS", true);
        foundIntent.putExtra("RSSI", device.getRssi());
        sendBroadcast(foundIntent);
        return;
    }


    protected RetStatus sendReadAllRequest() {
        _tRequestLock.lock();
        ILinkBridge linkBridge = getLinkBridge();

        RetStatus status;
        status = performBroadcast(linkBridge, (REQ_READ_ALL + RXX_TERM).getBytes());
        if (status != RetStatus.OK) {
            Logger.err("Failed to broadcast READ ALL request");
            _tRequestLock.unlock();
            return RetStatus.FAILED;
        }

        _tRequestLock.unlock();
        return RetStatus.OK;
    }



    protected RetStatus performBroadcast(ILinkBridge btBridge, byte data[]) {
//        if (data.length > _iSerialTxLen) {
//            return broadcastAsStream(btBridge, data);
//        }
        return broadcastDirectly(btBridge, data);
    }

    protected RetStatus broadcastDirectly(ILinkBridge btBridge, byte data[]) {
        return btBridge.broadcast(data);
    }

    /** Private Inner Classes **/
    private final BroadcastReceiver _receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (Constants.ACTION_DISCOVERED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short uRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short)0);
                String address = device.getAddress();
                String name = device.getName();

                if (name == null) {
                    name = "";
                }

                for (FoundDevice item : _foundDevices) {
                    if (item.addressMatches(address)) {
                        item.setName(name);
                        item.setIsLost(false);
                        item.setRssi(uRssi);

                        Logger.info("Updated found device: " + item.toString());

						/* Broadcast our received data for our receivers */
                        broadcastDeviceFound(item);
                        return;
                    }
                }
                FoundDevice newItem = new FoundDevice(name, address);
                newItem.setIsLost(false);
                newItem.setRssi(uRssi);
                _foundDevices.add(newItem);

                Logger.info("Added found device: " + newItem.toString());
                broadcastDeviceFound(newItem);
            }
        }
    };
}

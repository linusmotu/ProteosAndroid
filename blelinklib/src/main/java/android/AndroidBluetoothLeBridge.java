package android;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import interfaces.ILinkBridge;
import interfaces.LinkEventHandler;
import types.LinkState;
import types.RetStatus;
import utils.Constants;
import utils.Logger;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AndroidBluetoothLeBridge implements ILinkBridge {

	private static final int BLUNO_BAUD_RATE = 115200;
	private static final long MAX_BLE_SCAN_DURATION = 10000;
	private static final String BLUNO_MODEL_NUM_RESP_PREFIX = "DF BLUNO";
	private static final String BLUNO_COMMAND_PASSWORD_STR = "AT+PASSWOR=DFRobot\r\n";
	private static final String BLUNO_COMMAND_BAUD_RATE_STR = "AT+CURRUART=" + BLUNO_BAUD_RATE + "\r\n";

	private static final int VALUE_UNKNOWN = -65535;

	private final UUID UUID_SERIAL 		= UUID.fromString("0000dfb1-0000-1000-8000-00805F9B34FB");
	private final UUID UUID_COMMAND 	= UUID.fromString("0000dfb2-0000-1000-8000-00805F9B34FB");
	private final UUID UUID_MODEL_NUM	= UUID.fromString("00002a24-0000-1000-8000-00805F9B34FB");

	private static AndroidBluetoothLeBridge _androidBluetoothBridge = null;

	private BluetoothAdapter 	_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private boolean 			_isServer 			= false;
	private boolean				_isScanning			= false;
	private boolean				_isRecvrRegistered	= false;
	private LinkState 			_state 				= LinkState.UNKNOWN;

	/* Device list maps */
	private HashMap<String, String> _pairedDevices 						= null;
	private HashMap<String, String> _discoveredDevices 					= null;
	private HashMap<String, BluetoothLeConnection> _currentConnections 	= null;

	/* Threads and Event Handlers */
	private LinkEventHandler _eventHandler = null;
	private Thread _waitingThread = null;

	private Context _context = null;

	private AndroidBluetoothLeBridge() {
		return;
	}

	public static AndroidBluetoothLeBridge getInstance() {
		if (_androidBluetoothBridge == null) {
			_androidBluetoothBridge =  new AndroidBluetoothLeBridge();
		}

		return _androidBluetoothBridge;
	}

	@Override
	public String getId() {
		return "bluetooth_le";
	}


	@Override
	public synchronized LinkState getState() {
		return _state;
	}

	@Override
	public RetStatus initialize(Object initObject, boolean isServer) {
		/* Allow connect only if we're coming from the unknown state */
		if (_state != LinkState.UNKNOWN) {
			Logger.warn("Already initialized()");
			return RetStatus.OK;
		}

		if (initObject == null) {
			return RetStatus.FAILED;
		}

		/* Treat the initObject as the _context */
		_context = (Context) initObject;
		if (!_bluetoothAdapter.isEnabled()) {
			Logger.err("Bluetooth is not enabled");
			return RetStatus.FAILED;
		}

		setupDeviceLists();

		/* Register our receivers */
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		_context.registerReceiver(_receiver, filter);

		_isRecvrRegistered  = true;

		/* Remember initialization settings (either server or client) */
		_isServer = isServer;

		/* Set the initial state to DISCONNECTED */
		setState(LinkState.DISCONNECTED);

		return RetStatus.OK;
	}

	@Override
	public void startDiscovery() {
		if (_isScanning) {
			Logger.warn("Service Discovery has already been started");
			return;
		}

		_bluetoothAdapter.startLeScan(_leScanCbf);
//		_bluetoothAdapter.getBluetoothLeScanner().startScan(_newLeScanCbf);
		_isScanning = true;

		/* Automatically terminate BLE scan after MAX_BLE_SCAN_DURATION */
//		new Handler().postDelayed(
//				new Runnable() {
//					@Override
//					public void run() {
//						if (_leScanCbf != null) {
//							stopDeviceDiscovery();
//						}
//					}
//				}, MAX_BLE_SCAN_DURATION
//		);
		Logger.dbg("Service Discovery started.");
	}

	@Override
	public void stopDiscovery() {
		if (!_isScanning) {
			Logger.warn("Service Discovery has not been started or has already been stopped");
			return;
		}

		_bluetoothAdapter.stopLeScan(_leScanCbf);
//		_bluetoothAdapter.getBluetoothLeScanner().stopScan(_newLeScanCbf);
		_isScanning = false;

		Logger.dbg("Service Discovery stopped.");
	}

	@Override
	public RetStatus connectDeviceByAddress(String address) {
		/* Allow listen only if started as a Bluetooth client */
		if (_isServer) {
			Logger.err("Not initialized as a Bluetooth client");
			return RetStatus.FAILED;
		}

		/* Allow connect only if we're coming from the disconnected state */
		if (_state != LinkState.DISCONNECTED) {
			Logger.err("Invalid state for connectDeviceByAddress()");
			return RetStatus.FAILED;
		}

		if (!BluetoothAdapter.checkBluetoothAddress(address)) {
			Logger.err("Invalid bluetooth hardware address: " + address);
			return RetStatus.FAILED;
		}

		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Logger.err("Invalid Bluetooth device address");
			return RetStatus.FAILED;
		}
		BluetoothLeConnection bleConn;
		if (_currentConnections.containsKey(address)) {
			if (_currentConnections.get(address).isConnected()) {
				Logger.warn("Already connected");
			}

			/* Attempt to re-connect using the existing inactive BLE connection */
			bleConn = _currentConnections.get(address);
			if (bleConn != null) {
				bleConn.disconnect();
			}
		} else {
			/* Connect using an entirely new BLE connection */
			bleConn = new BluetoothLeConnection(device.getName(), address);
		}

		_waitingThread = Thread.currentThread();
		/* Attempt to connect to this remote device */
		BluetoothGatt bleGatt = device.connectGatt(_context, false, bleConn.getCallbackHandler());
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
			/* Proceed normally for interrupts */
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		_waitingThread = null;

		/* If not connected, then exit unsuccessfully */
		if (!bleConn.isConnected()) {
			Logger.err("Connection unsuccessful: Exiting at state " + this.getState());
			return RetStatus.FAILED;
		}

		/* Save the BluetoothGatt reference in our BluetoothLeConnection object */
		bleConn.setBluetoothGatt(bleGatt);

		/* Add it to our list of current connections */
		_currentConnections.put(address, bleConn);

		return RetStatus.OK;
	}

	@Override
	public RetStatus disconnectDeviceByAddress(String address) {
		if (_currentConnections == null) {
			Logger.err("No connections found");
			return RetStatus.FAILED;
		}

		BluetoothLeConnection bleConn = _currentConnections.get(address);
		if (bleConn == null) {
			Logger.err("No connections found for this address: " + address);
			return RetStatus.FAILED;
		}

		/* Disconnect and close the connection */
		if (!bleConn.disconnect()) {
			Logger.err("Disconnect failed: " + address);
		}

		if (!bleConn.close()) {
			Logger.err("Close failed: " + address);
		}

		bleConn.setConnected(false);

		/* Remove it from the list */
		if (_currentConnections.remove(address) == null) {
			Logger.err("Device not found in list: " + address);
		}

		setState(LinkState.DISCONNECTED);

		return RetStatus.OK;
	}

	@Override
	public RetStatus broadcast(byte[] data) {
		/* Allow broadcast only if we're in the connected state */
		if (_state != LinkState.CONNECTED) {
			Logger.err("Invalid state for broadcast()");
			return RetStatus.FAILED;
		}

		if (_currentConnections == null) {
			Logger.err("Current connections list unavailable");
			return RetStatus.FAILED;
		}

		if (_currentConnections.isEmpty()) {
			Logger.err("Current connections list is empty");
			return RetStatus.FAILED;
		}

		/* Determine the actual length of the data, without NUL chars */
		int iDataLen;
		for (iDataLen = 0; iDataLen < data.length; iDataLen++) {
			if ( (data[iDataLen] <= 0) || (data[iDataLen] >= 128) ) {
				break;
			}
		}

		byte actualData[] = new byte[iDataLen];
		System.arraycopy(data, 0, actualData, 0, iDataLen);

		boolean bWriteFailed = false;
		for (Map.Entry<String, BluetoothLeConnection> conn : _currentConnections.entrySet()) {
			if (!conn.getValue().write(actualData)) {
				bWriteFailed = true;
			}
		}

		if (bWriteFailed) {
			return RetStatus.FAILED;
		}

		return RetStatus.OK;
	}

	@Override
	public RetStatus destroy() {
		if (stop() != RetStatus.OK)
		{
			Logger.err("Failed to destroy BluetoothBridge");
		}

		return RetStatus.OK;
	}

	@Override
	public RetStatus setEventHandler(LinkEventHandler eventHandler) {
		this._eventHandler = eventHandler;
		return RetStatus.OK;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private RetStatus stop() {
		if (_currentConnections != null) {
			for (String key : _currentConnections.keySet()) {
				BluetoothLeConnection bleConn = _currentConnections.get(key);

				/* Attempt to disconnect the connection */
				_waitingThread = Thread.currentThread();
				bleConn.disconnect();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					/* Allow normal interrupts */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
				_waitingThread = null;

				if (bleConn.isConnected()) {
					Logger.err("Failed to disconnect!");
				}

				/* Close the connection regardless */
				bleConn.close();
			}

			_currentConnections.clear();	/* Clear active connections */
		}

		_eventHandler = null;			/* Initialized by setEventHandler() */

		/* Unregister our receivers */
		if (_isRecvrRegistered) {
			_context.unregisterReceiver(_receiver);
			_isRecvrRegistered = false;
		}

		clearDeviceLists();

		setState(LinkState.UNKNOWN);

		return RetStatus.OK;
	}

	/* TODO Unused, may come into play once we need handling for disconnects */
	private RetStatus removeConnection(String address){
		if (address == null) {
			Logger.err("Invalid input parameter/s" +
					" in AndroidBluetoothBridge.removeConnection()");
			return RetStatus.FAILED;
		}

		if (_currentConnections == null) {
			return RetStatus.FAILED;
		}

		if (_currentConnections.isEmpty()) {
			return RetStatus.FAILED;
		}

		if (_currentConnections.containsKey(address)) {
			BluetoothLeConnection bleConn = _currentConnections.get(address);
			if (bleConn.isConnected()) {
				/* Attempt to disconnect the connection */
				_waitingThread = Thread.currentThread();
				bleConn.disconnect();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					/* Allow normal interrupts */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
				_waitingThread = null;

				if (bleConn.isConnected()) {
					Logger.err("Failed to disconnect!");
				}
			}

			/* Close the connection */
			bleConn.close();

			_currentConnections.remove(address);
		}

		setState(LinkState.DISCONNECTED);

		return RetStatus.OK;
	}

	private void clearDeviceLists() {

		if (_pairedDevices != null) {
			if (!_pairedDevices.isEmpty()) {
				_pairedDevices.clear();
			}
			_pairedDevices = null;
		}

		if (_discoveredDevices != null) {
			if (!_discoveredDevices.isEmpty()) {
				_discoveredDevices.clear();
			}
			_discoveredDevices = null;
		}

		if (_currentConnections != null) {
			if (!_currentConnections.isEmpty()) {
				_currentConnections.clear();
			}
			_currentConnections = null;
		}
	}

	private void setupDeviceLists() {
		_pairedDevices = new HashMap<String, String>();
		_discoveredDevices = new HashMap<String, String>();
		_currentConnections = new HashMap<String, BluetoothLeConnection>();
	}

	private synchronized void setState(LinkState state) {
		_state = state;
		Logger.dbg("Bluetooth Bridge State is now " + _state.toString());
	}

	private void notifyOnConnected(String name, String address) {
		setState(LinkState.CONNECTED);
		/* Notify the event handler that we've connected */
		if (_eventHandler != null) {
			_eventHandler.onConnected(name, address);
		}
	}

	private void notifyOnDisconnected(String name, String address) {
		setState(LinkState.DISCONNECTED);
		/* Notify the event handler that we've disconnected */
		if (_eventHandler != null) {
			_eventHandler.onDisconnected(name, address);
		}
	}

	private void notifyOnBluetoothDeactivated() {
		stop();

		/* Notify the event handler that Bluetooth has been deactivated */
		if (_eventHandler != null) {
			_eventHandler.onDeactivated();
		}
	}

	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int iNewState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

				if ((iNewState == BluetoothAdapter.STATE_OFF) ||
						(iNewState == BluetoothAdapter.STATE_TURNING_OFF)) {
						/* If our Bluetooth is deactivating, then we have to notify our users */
					notifyOnBluetoothDeactivated();
				}
			}
		}
	};

	private BluetoothAdapter.LeScanCallback _leScanCbf = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			Logger.dbg("onLeScan() triggered");
			if (device == null) {
				return;
			}

			if (_discoveredDevices == null) {
				return;
			}

			if (!_discoveredDevices.containsValue(device.getAddress())) {
				_discoveredDevices.put(device.getName(), device.getAddress());
			}

			if (_context == null) {
				Logger.err("No context for intent broadcast!");
			}

			Intent intent = new Intent(Constants.ACTION_DISCOVERED);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
			_context.sendBroadcast(intent);
		}
	};

	private class BluetoothGattRequest {
		private BluetoothGattCharacteristic _characteristic;
		private boolean _isWrite = false;
		private byte _data[];

		BluetoothGattRequest(BluetoothGattCharacteristic characteristic,
										 byte data[]) {
			_characteristic = characteristic;
			_data = data;
			_isWrite = true;
		}

		BluetoothGattRequest(BluetoothGattCharacteristic characteristic,
										 String data) {
			_characteristic = characteristic;
			_data = data.getBytes();
			_isWrite = true;
		}

		BluetoothGattRequest(BluetoothGattCharacteristic characteristic) {
			_characteristic = characteristic;
			_data = null;
			_isWrite = false;
		}

		BluetoothGattCharacteristic getCharacteristic() {
			return _characteristic;
		}

		String getCharacteristicName() {
			UUID uuid = _characteristic.getUuid();
			if (uuid.equals(UUID_COMMAND)) {
				return "COMMAND";
			} else if (uuid.equals(UUID_MODEL_NUM)) {
				return "MODEL NO";
			} else if (uuid.equals(UUID_SERIAL)) {
				return "SERIAL";
			}

			return "UNKNOWN";
		}

		public byte[] getData() {
			return _data;
		}

		boolean isWrite() { return _isWrite; }
	}

	private class BluetoothLeConnection {
		private String _deviceName = "";
		private String _deviceAddress = "";
		private BluetoothGatt _bluetoothGatt = null;
		private int _iSerialTxRate = Constants.DEF_TX_RATE;
		private int _rssi = -1;
		private boolean _isConnected = false;
		private ByteArrayOutputStream _incomingData = null;
		private byte[] _dataBuffer = null;
		boolean _bInternalQueueFailed = false;

		private List<BluetoothGattCharacteristic> _gattCharacteristics = new ArrayList<>();
		private BluetoothGattCharacteristic _modelNumCharacteristic = null;
		private BluetoothGattCharacteristic _serialPortCharacteristic = null;
		private BluetoothGattCharacteristic _commandCharacteristic = null;
		private BluetoothGattCharacteristic _targetCharacteristic = null;

		private List<BluetoothGattRequest> _gattTaskQueue = null;

		private BluetoothGattCallbackHandler _callbackHandler = new BluetoothGattCallbackHandler();

		BluetoothLeConnection(String name, String address) {
			_deviceName = name;
			_deviceAddress = address;
			_gattTaskQueue = new ArrayList<BluetoothGattRequest>();
		}

		BluetoothGattCallback getCallbackHandler() {
			return _callbackHandler;
		}

		void setBluetoothGatt(BluetoothGatt gatt) {
			_bluetoothGatt = gatt;
		}

		void setConnected(boolean isConnected) {
			_isConnected = isConnected;
		}

		boolean isConnected() {
			return _isConnected;
		}

		String getDeviceName() {
			return _deviceName;
		}

		String getDeviceAddress() {
			return _deviceAddress;
		}

		boolean disconnect() {
			if (_bluetoothGatt == null) {
				Logger.err("disconnect() : No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			_bluetoothGatt.disconnect();

			return true;
		}

		boolean close() {
			if (_bluetoothGatt == null) {
				Logger.err("close() : No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			_bluetoothGatt.close();

			return true;
		}

		boolean write(byte data[]) {
			if (!_isConnected) {
				 Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bluetoothGatt == null) {
				Logger.err("write() : No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bInternalQueueFailed) {
				Logger.err("Internal queue failed on previous attempts");
				_bInternalQueueFailed = false;
				return false;
			}

			/* Write the data using our serial port characteristic */
			if (_targetCharacteristic != _serialPortCharacteristic) {
				Logger.err("Connection is busy");
				return false;
			}

			/* Add it to the writing queue */
			_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, data));

			/* Nudge the Queue */
			return !nudgeQueue();
		}

		boolean read(BluetoothGattCharacteristic characteristic) {
			if (!_isConnected) {
				Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bluetoothGatt == null) {
				Logger.err("read() : No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			/* Add it to the read queue */
			_gattTaskQueue.add(new BluetoothGattRequest(characteristic));

			return true;
		}

		private boolean nudgeQueue() {
			synchronized (this) {
				if (_gattTaskQueue.isEmpty()) {
					return true;
				}

				if (!_isConnected) {
					Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
					Logger.dbg("nudgeQueue() invoked; elements in queue: " + _gattTaskQueue.size());
					return false;
				}

				if (_bluetoothGatt == null) {
					Logger.err("nudgeQueue() : No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
					return false;
				}

				/* Retrieve the write request and reconstruct the characteristic */
				BluetoothGattRequest request = _gattTaskQueue.get(0);
				BluetoothGattCharacteristic characteristic = request.getCharacteristic();
				Logger.dbg("Processing request: " + request.getCharacteristicName() +
						", isWrite=" + request.isWrite());
				if (request.isWrite()) {
					byte data[] = request.getData();
					characteristic.setValue(data);

					/* Write the characteristic through GATT */
					int iRetries = Constants.DEF_MAX_RETRIES;
					StringBuilder output = new StringBuilder();
					while (!_bluetoothGatt.writeCharacteristic(characteristic)) {
						output.append("Write failed: ");
						for (byte b : data) {
							output.append((int)(b));
							output.append(" ");
						}
						Logger.warn(output.toString());

						iRetries--;
						if (iRetries <= 0) {
							Logger.err("Write Failed: No more retries left for BLE Write");
							break;
						}

						/* Wait a bit before the next attempt */
						try {
							Thread.sleep(30);
						} catch (InterruptedException e) {
							/* Do nothing */
						}
					}
					Logger.dbg("Write finished: " + new String(data));
				} else {
					/* Read the characteristic through GATT */
					if (!_bluetoothGatt.readCharacteristic(characteristic)) {
						Logger.err("Read failed");
						return false;
					}
					Logger.dbg("Read finished");
				}

				/* Remove it from the queue once successfully sent */
				_gattTaskQueue.remove(request);

				try {
					Thread.sleep(_iSerialTxRate);
				} catch (InterruptedException e) {
					/* Allow normal interruptions */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
			}

			return true;
		}

		private boolean parseReceived(BluetoothGattCharacteristic characteristic) {
			if (_incomingData == null) {
				Logger.dbg("New data incoming...");
				_dataBuffer = null;
				_incomingData = new ByteArrayOutputStream();
			}

			try {
				_incomingData.write(characteristic.getValue());
				if (hasTerminatingChar(characteristic.getValue())) {
					/* Retrieve the byte array */
					_dataBuffer = _incomingData.toByteArray();

					/* Notify our event handler/s */
					if (_eventHandler != null) {
						_eventHandler.onDataReceived(_deviceName, _deviceAddress, _dataBuffer);
					}

					/* Close our old data stream */
					_incomingData.close();
					_incomingData = null;
				}
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
			}

			return true;
		}

		private boolean hasTerminatingChar(byte[] dataArr) {
			for (byte b : dataArr) {
				if ((b == ';') || (b == '\r') || (b == '\n')) {
					return true;
				}
			}

			return false;
		}

		private class BluetoothGattCallbackHandler extends BluetoothGattCallback {
			@Override
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
				try {
					if (newState == BluetoothProfile.STATE_CONNECTED) {
						_isConnected = true;
						/* Start discovery of services as well */
						if (!gatt.discoverServices()) {
							Logger.err("Could not discover services for connection");
						}
					} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
						_isConnected = false;
						_gattTaskQueue.clear();

						/* Unset our previously known characteristics */
						_modelNumCharacteristic = null;
						_serialPortCharacteristic = null;
						_commandCharacteristic = null;

						if (gatt.getDevice() == null) {
							Logger.warn("Received NULL BluetoothDevice reference");
							notifyOnDisconnected("", "");
						} else {
							notifyOnDisconnected(gatt.getDevice().getName(), gatt.getDevice().getAddress());
						}

						/* Interrupt any waiting threads */
						if (_waitingThread != null) {
							_waitingThread.interrupt();
						}
					}

					Logger.dbg("Bluetooth LE Conn State Changed: " + newState);
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					e.printStackTrace();
				}
			}

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				byte binaryData[] = characteristic.getValue();
				if (_targetCharacteristic == _modelNumCharacteristic) {
					/* Handle data pushed through the model number characteristic */
					String data = new String(binaryData);

					if (data.toUpperCase().startsWith(BLUNO_MODEL_NUM_RESP_PREFIX)) {
						gatt.setCharacteristicNotification(_targetCharacteristic, false);

						_targetCharacteristic = _commandCharacteristic;
						_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, BLUNO_COMMAND_PASSWORD_STR));
						_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, BLUNO_COMMAND_BAUD_RATE_STR));

						nudgeQueue();

						_targetCharacteristic = _serialPortCharacteristic;
						gatt.setCharacteristicNotification(_targetCharacteristic, true);

						/* At this point our connection is ready */
						/* Interrupt any waiting threads */
						if (_waitingThread != null) {
							_waitingThread.interrupt();
						}
						notifyOnConnected(getDeviceName(), getDeviceAddress());
					} else {
						Logger.err("Unexpected data received: " + data);
					}
				} else if (_targetCharacteristic == _serialPortCharacteristic) {
					/* Handle data pushed through the serial port characteristic */
					if (_eventHandler != null) {
						_eventHandler.onDataReceived(_deviceName, _deviceAddress, binaryData);
					}
				}

				Logger.dbg("Read from " + characteristic.getUuid() + ": " + new String(binaryData));
			}

			@Override
			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				/* Attempt to update our remote RSSI everytime a WRITE is initiated */
				if (gatt != null) {
					if (!gatt.readRemoteRssi()) {
						Logger.err("Failed to initiate RSSI read");
					}
				}

				if (status == BluetoothGatt.GATT_SUCCESS) {

					Logger.dbg("Wrote at " + characteristic.getUuid() + " with " + new String(characteristic.getValue()));
					/* Write the next set of data once this is successful */
					nudgeQueue();
				}
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				Logger.dbg("onServicesDiscovered() called");
				if (status != BluetoothGatt.GATT_SUCCESS) {
					Logger.err("Failed to discover services");
					return;
				}
				/* Clear our previous list of characteristics */
				_gattCharacteristics.clear();

				/* Upon successful discovery of services, check and cache the ones we can use */
				UUID uuid;
				for (BluetoothGattService gattService : gatt.getServices()) {
					//uuid = gattService.getUuid();
					/* Loop through the service's characteristics */
					for (BluetoothGattCharacteristic gattChar : gattService.getCharacteristics()) {
						uuid = gattChar.getUuid();
						Logger.dbg("Processing: " + uuid);

						if (uuid.equals(UUID_MODEL_NUM)) {
							_modelNumCharacteristic = gattChar;
						} else if (uuid.equals(UUID_SERIAL)) {
							_serialPortCharacteristic = gattChar;
						} else if (uuid.equals(UUID_COMMAND)) {
							_commandCharacteristic = gattChar;
						}

						_gattCharacteristics.add(gattChar);
					}
				}

				/* Check if we were able to get all the characteristics needed by DFRobot's Bluno */
				if ((_modelNumCharacteristic == null) || (_serialPortCharacteristic == null) ||
						(_commandCharacteristic == null) ) {
					Logger.err("Missing characteristic required by Bluno devices");
					return;
				}

				Logger.dbg("Get characteristics done");

				/* Read the model number off our Bluno device */
				_targetCharacteristic = _modelNumCharacteristic;
				gatt.setCharacteristicNotification(_targetCharacteristic, true);

				_bluetoothGatt = gatt; // TODO Hacky
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Logger.warn("Read delay interrupted");
				}
				read(_targetCharacteristic);
				nudgeQueue();
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				super.onCharacteristicChanged(gatt, characteristic);
				if (!parseReceived(characteristic)) {
					Logger.err("Failed to parse received data");
				}
			}

			@Override
			public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					_rssi = rssi;
					Logger.dbg("RSSI: " + _rssi);
				}
				super.onReadRemoteRssi(gatt, rssi, status);
			}
		}
	}
}

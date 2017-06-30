package interfaces;

import types.LinkState;
import types.RetStatus;

public interface ILinkBridge {
	String getId();
	LinkState getState();
	RetStatus initialize(Object initObject, boolean isServer);
	void startDiscovery();
	void stopDiscovery();
	RetStatus connectDeviceByAddress(String address);
	RetStatus disconnectDeviceByAddress(String address);
	RetStatus broadcast(byte[] data);
	RetStatus destroy();
	RetStatus setEventHandler(LinkEventHandler eventHandler);
}

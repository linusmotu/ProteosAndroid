package interfaces;

import types.LinkState;
import types.RetStatus;

public interface ILinkBridge {
	public String getId();
	public String getPlatform();
	public LinkState getState();
	public String getLocalName();
	public String getLocalAddress();
	public boolean isReady();
	public int getRssi();
	public int getTransmitRateCap();
	public RetStatus initialize(Object initObject, boolean isServer);
	public void startDiscovery();
	public void stopDiscovery();
	public RetStatus listen();
	public RetStatus connectDeviceByAddress(String address);
	public RetStatus connectDeviceByName(String name);
	public RetStatus disconnectDeviceByAddress(String address);
	public RetStatus broadcast(byte[] data);
	public RetStatus broadcastRaw(byte[] data);
	public RetStatus read(String address);
	public RetStatus destroy();
	public RetStatus setEventHandler(LinkEventHandler eventHandler);
	public RetStatus setTransmitRateCap(int iTxRate);
}

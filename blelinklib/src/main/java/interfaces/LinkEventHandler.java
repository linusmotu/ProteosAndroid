package interfaces;

public interface LinkEventHandler {
	void onConnected(String name, String address);
	void onDisconnected(String name, String address);
	void onDeactivated();
	void onDataReceived(String name, String address, byte[] data);
}

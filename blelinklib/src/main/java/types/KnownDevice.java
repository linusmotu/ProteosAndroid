package types;

import utils.Logger;

public class KnownDevice {

	public static enum DeviceStatus { UNKNOWN, NOT_FOUND, FOUND };
	
	private String 		 _name 	 			= "";
	private String 		 _addr 	 			= "";
	private String 		 _reverseAddr 	 	= "";
	private DeviceStatus _status 			= DeviceStatus.UNKNOWN;
	private boolean 	 _isRegistered  	= false;
	private boolean		 _isAddrReversed	= false;
	
	private KnownDevice(String name, String address, DeviceStatus status) {
		this(name, address);
		_status = status;
	}

	private void setAddressReversed(boolean isReversed) {
		Logger.warn("The local bluetooth device may be reversing MAC addresses");
		_isAddrReversed = isReversed;
	}

	KnownDevice(String name, String address) {
		_name = name;
		_addr = address;
		_reverseAddr = KnownDevice.getReversedMacAddress(address);
	}
	
	public void setName(String name) {
		_name = name;
	}
	
	public boolean addressMatches(String address) {
		/* Check if we can match with the reversed address as well */
		if (_addr.equals(address)) {
			return true;
		}
		
		/* Check if we can match with the reversed address as well */
		if (_reverseAddr.equals(address)) {
			setAddressReversed(true);
			return true;
		}
		
		return false;
	}
	
	public String getName() {
		return _name;
	}
	
	public String getAddress() {
		return _addr;
	}

	public String toString() {
		String prefix = _isRegistered ? "[R] " : "[U] ";
		return (prefix + _name + "\n" + _addr + "\n" + _status.toString());
	}

	private static String getReversedMacAddress(String address) {
		StringBuilder newAddr = new StringBuilder();
		int targIdx = 0;
		int offset = 2;

		targIdx = address.length() - offset;
		while (targIdx >= 0) {

			newAddr.append(address.charAt(targIdx++));
			newAddr.append(address.charAt(targIdx));

			offset += 3;
			targIdx = address.length() - offset;

			if (targIdx >= 0) {
				newAddr.append(":");
			}
		}

		return newAddr.toString();
	}
}

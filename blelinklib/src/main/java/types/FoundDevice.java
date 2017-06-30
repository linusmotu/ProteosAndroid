package types;

public class FoundDevice extends KnownDevice {
	private long 	_lReportWindow = 0;
	private boolean _isLost = false;
	private short	_uRssi	= 0;

	public FoundDevice(String name, String address) {
		super(name, address);
	}

	public short getRssi() {
		return _uRssi;
	}

	public void setIsLost(boolean isLost) {
		_isLost = isLost;
	}

	public void setRssi(short uRssi) {
		_uRssi = uRssi;
	}
	
	/* TODO Should probably make all of this thread-safe */

	public String toString() {
		return getName() + "   (" + _uRssi +")\n" + getAddress();
	}
}

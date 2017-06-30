package types;

/**
 * Created by francis on 3/23/16.
 */
public class ReportModeInfo {
    private int _channel = 0;
    private long _maxWaitTime = 60000;
    private long _syncTime = 0;

    public ReportModeInfo(int channel, long waitTime, long syncTime) {
        _channel = channel;
        _maxWaitTime = waitTime;
        _syncTime = syncTime;
    }

    public void setChannel(int channel) {
        _channel = channel;
    }

    public void setMaxWaitTime(long time) {
        _maxWaitTime = time;
    }

    public void setSyncTime(long time) {
        _syncTime = time;
    }
}

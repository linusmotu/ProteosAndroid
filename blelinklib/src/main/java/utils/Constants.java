package utils;

/**
 * Created by francis on 5/5/16.
 */
public class Constants {
    public static final int DEF_SEG_LEN = 300;
    public static final int DEF_TX_RATE = 2;
    public static final int DEF_MAX_RETRIES = 5;
    public static final int MAX_TX_LEN = 20;
    public static final int IDX_PAYLOAD_DATA_OFFS   = 4;

    /* Interrupt reason values */
    public static final int RSN_UNKNOWN         = 0;
    public static final int RSN_STOP_REQUESTED  = 1;
    public static final int RSN_DATA_ACK        = 2;
    public static final int RSN_DATA_FAIL       = 3;
    public static final int RSN_DATA_EXISTS     = 4;
    public static final int RSN_DATA_COMPLETE   = 5;
    public static final int RSN_UPLOAD_CANCEL   = 6;

    public static final int REQUEST_BROWSE_FILE = 1;
    public static final int RESULT_BROWSE_OK = 2;
    public static final int RESULT_BROWSE_CANCEL = -1;

    public static final int MSG_RESP_SERVICE_STATE	= 100;
    public static final int MSG_RESP_LINK_STATE	    = 101;
    public static final int MSG_RESP_ACTIVE_TASKS	= 102;

    public static final int PAYLOAD_ENC_TYPE_UNK = 0;
    public static final int PAYLOAD_ENC_TYPE_B64 = 1;
    public static final int PAYLOAD_ENC_TYPE_BIN = 2;

    public static final String ACTION_DISCOVERED = "com.aquosense.blelinklib.action.DEVICE_DISCOVERED";
}

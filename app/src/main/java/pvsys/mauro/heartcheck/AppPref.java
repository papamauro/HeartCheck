package pvsys.mauro.heartcheck;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

public class AppPref {
    private final static Logger LOG = new Logger(AppPref.class.getSimpleName());

    public static final String BONDED_DEVICE_ADDRESS = "BONDED_DEVICE_ADDRESS";
    public static final String BONDED_DEVICE_NAME = "BONDED_DEVICE_NAME";
    public static final String BONDED_DEVICE_TYPE = "BONDED_DEVICE_TYPE";

    private final SharedPreferences pref;
    private final Context context;
    public AppPref(Context context) {
        this.context = context;
        pref = PreferenceManager.getDefaultSharedPreferences(this.context);
    }

    public AppPref(Activity activity) {
        this.context = activity.getApplicationContext();
        pref = PreferenceManager.getDefaultSharedPreferences(this.context);
    }

    public void setPreferredDevice(Device device) {
        pref.edit().putString(BONDED_DEVICE_ADDRESS, device.getAddress()).apply();
        pref.edit().putString(BONDED_DEVICE_NAME, device.getName()).apply();
        pref.edit().putString(BONDED_DEVICE_TYPE, device.getType()).apply();

        LOG.debug("set preferred device to: " + device.getAddress() + " - " + device.getName() + " - " + device.getType() + " - " );
    }

    public Device getPreferredDevice() {
        Device device = null;
        String address = pref.getString(BONDED_DEVICE_ADDRESS, null);
        String name = pref.getString(BONDED_DEVICE_NAME, null);
        String type = pref.getString(BONDED_DEVICE_TYPE, null);
        if(address!=null && name!=null && type!=null) {
            device = new Device(address, name, type);
        }
        return device;
    }
}

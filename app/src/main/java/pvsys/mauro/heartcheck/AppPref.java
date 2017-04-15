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

    public void setPreferredDevice(String address) {
        pref.edit().putString(BONDED_DEVICE_ADDRESS, address).apply();

    }

    public String getPreferredDevice() {
        return pref.getString(BONDED_DEVICE_ADDRESS, null);
    }
}

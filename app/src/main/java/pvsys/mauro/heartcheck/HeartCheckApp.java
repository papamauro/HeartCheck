package pvsys.mauro.heartcheck;


import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class HeartCheckApp extends Application {

    public static final String BONDED_DEVICE_ADDRESS = "BONDED_DEVICE_ADDRESS";
    public static final String BONDED_DEVICE_TYPE = "BONDED_DEVICE_TYPE";

    private final static Logger LOG = new Logger(HeartCheckApp.class.getSimpleName());

    private static Context context;
    private static SharedPreferences pref;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        pref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static Context getContext() {
        return context;
    }


    public static void setPreferredDevice(String address) {
        pref.edit().putString(BONDED_DEVICE_ADDRESS, address).apply();
        //pref.edit().putString(BONDED_DEVICE_TYPE, address).apply();
    }

    public static String getPreferredDeviceAddress() {
        return pref.getString(BONDED_DEVICE_ADDRESS, null);
    }

    public static String getPreferredDeviceType() {
        return pref.getString(BONDED_DEVICE_TYPE, null);
    }

}

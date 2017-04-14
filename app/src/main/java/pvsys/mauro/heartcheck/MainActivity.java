package pvsys.mauro.heartcheck;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import static pvsys.mauro.heartcheck.Callbacks.Callback;
import static pvsys.mauro.heartcheck.Callbacks.Callback;
public class MainActivity extends AppCompatActivity {

    private final static Logger LOG = new Logger(AppCompatActivity.class.getSimpleName());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Device savedDevice =  new AppPref(this).getPreferredDevice();

        if (savedDevice != null) {
            LOG.debug("using saved device");
            connect(savedDevice);
        } else {
            MiBandDiscover btHelper = new MiBandDiscover(this, onDeviceDiscovered, true);
            btHelper.startBT();
        }
    }

    private Callback<DeviceCandidate> onDeviceDiscovered = new Callback<DeviceCandidate>() {
        @Override
        public void call(DeviceCandidate candidate) {
            MiBandPairing pairing = new MiBandPairing(candidate, MainActivity.this, onDevicePaired);
            pairing.pair();
        }
    };

    private Callback<Device> onDevicePaired = new Callback<Device>() {
        @Override
        public void call(Device device) {
            LOG.info("device paired");
            connect(device);
        }
    };

    private void connect(Device device) {
        LOG.info("connecting  to " + device.getAddress());
    }

}

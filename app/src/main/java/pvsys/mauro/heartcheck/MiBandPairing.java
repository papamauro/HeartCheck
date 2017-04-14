package pvsys.mauro.heartcheck;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import static pvsys.mauro.heartcheck.Callbacks.Callback;

public class MiBandPairing {

    private final static Logger LOG = new Logger(MiBandPairing.class.getSimpleName());

    private final DeviceCandidate deviceCandidate;
    private final Activity activity;
    private Callback<Device> onDevicePaired;

    private static final int REQ_CODE_USER_SETTINGS = 52;
    private static final String STATE_DEVICE_CANDIDATE = "stateDeviceCandidate";
    private static final long DELAY_AFTER_BONDING = 1000; // 1s
    private boolean isPairing;
    private String bondingMacAddress;


    public MiBandPairing(DeviceCandidate deviceCandidate, Activity activity, Callback<Device> onDevicePaired) {
        this.deviceCandidate = deviceCandidate;
        this.activity = activity;
        this.onDevicePaired = onDevicePaired;
    }

    public void pair() {
        startPairing();
    }

    private void attemptToConnect() {
        Looper mainLooper = Looper.getMainLooper();
        new Handler(mainLooper).postDelayed(new Runnable() {
            @Override
            public void run() {
                performApplicationLevelPair();
            }
        }, DELAY_AFTER_BONDING);
    }


    private void startPairing() {
        isPairing = true;


        IntentFilter filter = new IntentFilter(Device.ACTION_DEVICE_CHANGED);
        LocalBroadcastManager.getInstance(activity).registerReceiver(mPairingReceiver, filter);

        if (!shouldSetupBTLevelPairing()) {
            // there are connection problems on certain Galaxy S devices at least;
            // try to connect without BT pairing (bonding)
            attemptToConnect();
            return;
        }

        filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        activity.registerReceiver(mBondingReceiver, filter);

        performBluetoothPair(deviceCandidate);
    }

    private final BroadcastReceiver mPairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Device.ACTION_DEVICE_CHANGED.equals(intent.getAction())) {
                Device device = intent.getParcelableExtra(Device.EXTRA_DEVICE);
                LOG.debug("pairing activity: device changed: " + device);
                if (deviceCandidate.getMacAddress().equals(device.getAddress())) {
                    if (device.isInitialized()) {
                        pairingFinished(true, deviceCandidate);
                    } else if (device.isConnecting() || device.isInitializing()) {
                        LOG.info("still connecting/initializing device...");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mBondingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                LOG.info("Bond state changed: " + device + ", state: " + device.getBondState() + ", expected address: " + bondingMacAddress);
                if (bondingMacAddress != null && bondingMacAddress.equals(device.getAddress())) {
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        LOG.info("Bonded with " + device.getAddress());
                        bondingMacAddress = null;
                        attemptToConnect();
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        LOG.info("Bonding in progress with " + device.getAddress());
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        LOG.info("Not bonded with " + device.getAddress() + ", attempting to connect anyway.");
                        bondingMacAddress = null;
                        attemptToConnect();
                    } else {
                        LOG.warn("Unknown bond state for device " + device.getAddress() + ": " + bondState);
                        pairingFinished(false, deviceCandidate);
                    }
                }
            }
        }
    };

    private boolean shouldSetupBTLevelPairing() {
        return true; //TODO: it was an app preference with default = true
    }

    private void pairingFinished(boolean pairedSuccessfully, DeviceCandidate candidate) {
        LOG.debug("pairingFinished: " + pairedSuccessfully);
        if (!isPairing) {
            // already gone?
            return;
        }

        isPairing = false;
        Utils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(activity), mPairingReceiver);
        Utils.safeUnregisterBroadcastReceiver(activity, mBondingReceiver);

        if (pairedSuccessfully) {
            // remember the device since we do not necessarily pair... temporary -- we probably need
            // to query the db for available devices in ControlCenter. But only remember un-bonded
            // devices, as bonded devices are displayed anyway.
            String macAddress = deviceCandidate.getMacAddress();
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress);
            if (device != null && device.getBondState() == BluetoothDevice.BOND_NONE) {
                //TODO : saving moved, check it if it works
            }
        }
    }

    private void stopPairing() {
        // TODO
        isPairing = false;
    }

    protected void performBluetoothPair(DeviceCandidate deviceCandidate) {
        BluetoothDevice device = deviceCandidate.getDevice();

        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            //GB.toast(getString(R.string.pairing_already_bonded, device.getName(), device.getAddress()), Toast.LENGTH_SHORT, GB.INFO);
            performApplicationLevelPair();
            return;
        }

        bondingMacAddress = device.getAddress();
        if (bondState == BluetoothDevice.BOND_BONDING) {
            //GB.toast(this, getString(R.string.pairing_in_progress, device.getName(), bondingMacAddress), Toast.LENGTH_LONG, GB.INFO);
            return;
        }

        //GB.toast(this, getString(R.string.pairing_creating_bond_with, device.getName(), bondingMacAddress), Toast.LENGTH_LONG, GB.INFO);
        if (!device.createBond()) {
            //GB.toast(this, getString(R.string.pairing_unable_to_pair_with, device.getName(), bondingMacAddress), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    private void performApplicationLevelPair() {
        //GBApplication.deviceService().disconnect(); // just to make sure...
        //MI1S, MIBAND, 88:0F:10:EC:46:09
        Device device = new Device(deviceCandidate.getDevice().getAddress(), deviceCandidate.getName(), deviceCandidate.getDeviceType());
        if (device != null) {
            //GBApplication.deviceService().connect(device, true);
            new AppPref(activity).setPreferredDevice(device);
            LOG.debug("pairingFinished - preference saved " + device.getAddress());
            onDevicePaired.call(device);
        } else {
            //GB.toast(this, "Unable to connect, can't recognize the device type: " + deviceCandidate, Toast.LENGTH_LONG, GB.ERROR);
        }
    }
}

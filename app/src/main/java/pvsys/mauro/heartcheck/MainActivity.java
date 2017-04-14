package pvsys.mauro.heartcheck;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.MATCH_MODE_STICKY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static pvsys.mauro.heartcheck.Callbacks.Callback;
import static pvsys.mauro.heartcheck.Callbacks.Callback;
public class MainActivity extends AppCompatActivity {

    private final static Logger LOG = new Logger(AppCompatActivity.class.getSimpleName());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        testAlone();
    }

    public static final int REQUEST_ENABLE_BT = 9;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothGatt bluetoothGatt;





    private void testAlone() {
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter != null) {
            if (!btAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }else{
                bluetoothSetupDone();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    bluetoothSetupDone();
                } else {
                    LOG.error("bluetooth activation not performed");
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

    }

    private void bluetoothSetupDone(){
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            LOG.error("bluetooth not available");
        }
        LOG.info("bluetooth available");
        deviceScan();
        //OR  BluetoothAdapter.getRemoteDevice(String)
    }

    private void deviceScan() {
        LOG.info("scan");
        ScanCallback leScanCallback = new ScanCallback() {
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                try {
                    ScanRecord scanRecord = result.getScanRecord();
                    ParcelUuid[] uuids = null;
                    if (scanRecord != null) {
                        //logMessageContent(scanRecord.getBytes());
                        List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
                        if (serviceUuids != null) {
                            uuids = serviceUuids.toArray(new ParcelUuid[0]);
                        }
                    }
                    LOG.warn(result.getDevice().getName() + ": " + ((scanRecord != null) ? scanRecord.getBytes().length : -1));
                    handleDeviceFound(result.getDevice(), (short) result.getRssi(), uuids);
                    btAdapter.getBluetoothLeScanner().stopScan(this);
                } catch (NullPointerException e) {
                    LOG.warn("Error handling scan result", e);
                    btAdapter.getBluetoothLeScanner().stopScan(this);
                }
            }
        };
        List<ScanFilter> allFilters = new ArrayList<>();
        ParcelUuid mi2Service = new ParcelUuid(MiBandService.UUID_SERVICE_MIBAND2_SERVICE);
        allFilters.add(new ScanFilter.Builder().setServiceUuid(mi2Service).build());
        ParcelUuid miService = new ParcelUuid(MiBandService.UUID_SERVICE_MIBAND_SERVICE);
        allFilters.add(new ScanFilter.Builder().setServiceUuid(miService).build());

        ScanSettings settings = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings = new ScanSettings.Builder()
                    .setScanMode(SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(MATCH_MODE_STICKY)
                    .build();
        } else {
            settings = new ScanSettings.Builder()
                    .setScanMode(SCAN_MODE_LOW_LATENCY)
                    .build();
        }

        btAdapter.getBluetoothLeScanner().startScan(allFilters, settings, leScanCallback);
    }


    private void handleDeviceFound(BluetoothDevice device, short rssi, ParcelUuid[] uuids) {
        LOG.info("found device: " + device.getName() + " (" + device.getAddress() + ") ");
        device.createBond();
        LOG.info("bonding");
        while(device.getBondState() != device.BOND_BONDED) {
            Thread.yield();
        }
        LOG.info("bonded");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        bluetoothGatt = device.connectGatt( this.getApplicationContext(), true, btleGattCallback); //changed false->true for an attempt
    }

    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            LOG.error("YEEEEEEEE - characteristic changed ");
            //byte[] data = characteristic.getValue();
            //LOG.info("characteristic changed " + characteristic.getUuid() + " " + Arrays.toString(data));
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            LOG.info("state changed to " + newState + " for " + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                LOG.info("connected successfully to" + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
                LOG.info("discovering services");
                gatt.discoverServices();
            }
        }

        private BluetoothGattCharacteristic heartRateCtrlChar = null;
        private BluetoothGattCharacteristic heartRateMeasureChar = null;



//        protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
//            builder.add(new SetDeviceStateAction(getDevice(), State.INITIALIZING, getContext()));
//            enableNotifications(builder, true)
//                    .setLowLatency(builder)
//                    .readDate(builder) // without reading the data, we get sporadic connection problems, especially directly after turning on BT
//                    .pair(builder)
//                    .requestDeviceInfo(builder)
//                    .sendUserInfo(builder)
//                    .checkAuthenticationNeeded(builder, getDevice())
//                    .setWearLocation(builder)
//                    .setHeartrateSleepSupport(builder)
//                    .setFitnessGoal(builder)
//                    .enableFurtherNotifications(builder, true)
//                    .setCurrentTime(builder)
//                    .requestBatteryInfo(builder)
//                    .setHighLatency(builder)
//                    .setInitialized(builder);
//            return builder;
//        }

        int initState = 0;
        BTDeviceChannel deviceChannel;

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(initState == 2) {
                LOG.info(initState + ") pairing");
                deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_PAIR , new byte[] {2});
                initState++;
            } else if(initState == 4) {
                LOG.info(initState + ") reading gap info");
                deviceChannel.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_GAP_DEVICE_NAME);
                initState++;
            } else if(initState == 5) {
                LOG.info(initState + ") write user info");
                deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_USER_INFO, new byte[] {-10 , -28 , 99 , 92 , 0 , 0 , -81 , 70 , 0 , 4 , 0 , 49 , 53 , 53 , 48 , 48 , 53 , 48 , 53 , 37 } );
                initState++;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if(initState == 1) {
                LOG.info(initState + ") reading time");
                deviceChannel.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_DATE_TIME);
                initState++;
            } else if(initState == 3) {
                LOG.info(initState + ") reading device info");
                deviceChannel.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_DEVICE_INFO);
                initState++;
            } else if(initState == 6) {
                LOG.info(initState + ") write location");
                deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT , new byte[]{
                        MiBandService.COMMAND_SET_WEAR_LOCATION,
                        (byte) 0 // left / 1 for right
                });
                initState++;
            } else if(initState == 7) {
                LOG.info(initState + ") write heart rate control");
                deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT, MiBandService.startHeartMeasurementSleep);
                initState++;
            } else if(initState == 8) {
                LOG.info(initState + ") registering all together - awww");
                deviceChannel.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT, true );
                deviceChannel.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_REALTIME_STEPS, true );
                deviceChannel.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_ACTIVITY_DATA, true );
                deviceChannel.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_BATTERY, true );
                deviceChannel.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_SENSOR_DATA, true );
                deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS , MiBandService.getHighLatency());
                initState++;
            }


        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            LOG.info("some services discovered for " + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
            deviceChannel = new BTDeviceChannel(gatt);

            try {Thread.sleep(6000);} catch (InterruptedException e) {e.printStackTrace();}
            initState = 0;
            LOG.info(initState + ") setting low latency");
            deviceChannel.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS , MiBandService.getLowLatency());
            initState++;
        }


    };





    private void useGB() {
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

package pvsys.mauro.heartcheck;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicInteger;


public class PrimaryFragment extends Fragment {

    private final static Logger LOG = new Logger(AppCompatActivity.class.getSimpleName());


    public static final int REQUEST_ENABLE_BT = 9;
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice bluetoothDevice;

    private BTExecutor btExecutor;
    private Button connectButton;
    private final AtomicInteger bpm = new AtomicInteger();

    public Handler handler = new Handler(){
        public void handleMessage(Message msg){
            TextView bpmView = (TextView) getView().findViewById(R.id.view_bpm);
            bpmView.setText(""+ bpm.get());
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.primary_layout,null);




        connectButton= (Button) rootView.findViewById(R.id.button_connect);


        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onConnectButtonClick(connectButton);
            }
        });

        return rootView;
    }


    private void startBluetooth() {
        btManager = (BluetoothManager)getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
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
        btManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            LOG.error("bluetooth not available");
        }
        LOG.info("bluetooth available");

        if (HeartCheckApp.getPreferredDeviceAddress()!=null) {
            BluetoothDevice device = btAdapter.getRemoteDevice(HeartCheckApp.getPreferredDeviceAddress());
            LOG.info("using saved device " + device.getAddress());
            handleDeviceFound(device);
        } else {
            LOG.info("Go back to BoundActivity");
            Intent intent = new Intent(getActivity(), BoundActivity.class);
            startActivity(intent);
        }
    }

    public void onConnectButtonClick(View button){
        Button mainButton = (Button) button;
        mainButton.setText("Stop Monitoring");
        startBluetooth();
    }

    private void handleDeviceFound(BluetoothDevice device) {
        LOG.info("found device: " + device.getName() + " (" + device.getAddress() + ") ");
        device.createBond();
        LOG.info("bonding");
        while(device.getBondState() != device.BOND_BONDED) {
            Thread.yield();
        }
        LOG.info("bonded");
        connect(device);
    }

    private void connect(BluetoothDevice device) {
        bluetoothDevice = device;
        bluetoothGatt = device.connectGatt(HeartCheckApp.getContext(), false, new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                LOG.info("state changed to " + newState + " for " + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    LOG.info("connected successfully to " + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
                    LOG.info("discovering services");
                    gatt.discoverServices();
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
                LOG.info("some services discovered for " + gatt.getDevice().getName() + " (" + gatt.getDevice().getAddress() + ") ");
                BTDeviceChannel deviceChannel = new BTDeviceChannel(gatt);
                btExecutor = new BTExecutor(deviceChannel);
                startDeviceInitialization();
            }


            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LOG.info("confirmed read characteristic " + characteristic.getUuid() + " val " + Arrays.toString(characteristic.getValue()));
                } else {
                    LOG.error("read characteristic " + characteristic.getUuid() + " failed");
                }
                btExecutor.taskCompleted();
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LOG.info("confirmed write characteristic " + characteristic.getUuid() + " val " + Arrays.toString(characteristic.getValue()));
                } else {
                    LOG.error("write characteristic " + characteristic.getUuid() + " failed");
                }
                btExecutor.taskCompleted();
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LOG.info("confirmed onDescriptorRead " + descriptor.getUuid());
                } else {
                    LOG.error("onDescriptorRead " + descriptor.getUuid() + " failed");
                }
                btExecutor.taskCompleted();
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LOG.info("confirmed onDescriptorWrite " + descriptor.getUuid());
                } else {
                    LOG.error("onDescriptorWrite " + descriptor.getUuid() + " failed");
                }
                btExecutor.taskCompleted();
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
                byte[] data = characteristic.getValue();
                LOG.info("YEEEEEEEE - characteristic changed " + characteristic.getUuid()+ " " + Arrays.toString(data));
                if(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
                    LOG.info("heartbeat: " + ((int)data[1]));

                    if((int)data[1] > 0){
                        bpm.set((int)data[1]);
                        handler.obtainMessage().sendToTarget();
                    }

                    btExecutor.taskCompleted();
                }
            }

        });
    }

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

    private void startDeviceInitialization() {
        LOG.info(1 + ") setting low latency");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS , MiBandService.getLowLatency());
        LOG.info(2 + ") reading time");
        btExecutor.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_DATE_TIME);
        LOG.info(3 + ") pairing");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_PAIR , new byte[] {2});
        LOG.info(4 + ") reading device info");
        btExecutor.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_DEVICE_INFO);
        LOG.info(5 + ") reading gap info");
        btExecutor.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_GAP_DEVICE_NAME);
        LOG.info(6 + ") write user info");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_USER_INFO, new byte[] {-10 , -28 , 99 , 92 , 0 , 0 , -81 , 70 , 0 , 4 , 0 , 49 , 53 , 53 , 48 , 48 , 53 , 48 , 53 , 37 } );
        LOG.info(7 + ") write location");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT , new byte[]{
                MiBandService.COMMAND_SET_WEAR_LOCATION,
                (byte) 0 // left / 1 for right
        });

        LOG.info(8 + ") control activation");
        //It seems it is necessary to set this initially to startHeartMeasurementSleep, although it won't work if not changed later
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT, MiBandService.startHeartMeasurementSleep);


        LOG.info(9 + ") registering ");
        btExecutor.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT, true );
        btExecutor.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_REALTIME_STEPS, true );
        btExecutor.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_ACTIVITY_DATA, true );
        btExecutor.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_BATTERY, true );
        btExecutor.registerToCharacteristic(MiBandService.UUID_CHARACTERISTIC_SENSOR_DATA, true );

        LOG.info(14 + ") setting time ");
        setCurrentTime();

        LOG.info(15 + ") read battery ");
        btExecutor.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_BATTERY);

        LOG.info(16 + ") set high latency ");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS , MiBandService.getHighLatency());

        //added just to try something
        LOG.info(17 + ") vibrate");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT, MiBandService.getDefaultNotification());

        LOG.info(18 + ") vibrate");
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT, MiBandService.startRealTimeStepsNotifications);
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT, MiBandService.startSensorRead);

        LOG.info(20 + ") ");
        btExecutor.readCharacteristic(MiBandService.UUID_CHARACTERISTIC_DATE_TIME);
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT, MiBandService.stopHeartMeasurementSleep);
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT, MiBandService.startHeartMeasurementContinuous);
    }

    private void setCurrentTime() {
        Calendar now = GregorianCalendar.getInstance();

        byte[] time = new byte[]{
                (byte) (now.get(Calendar.YEAR) - 2000),
                (byte) now.get(Calendar.MONTH),
                (byte) now.get(Calendar.DATE),
                // The mi-band device currently records sleep
                // only if it happens after 10pm and before 7am.
                (byte) 23, //now.get(Calendar.HOUR_OF_DAY),
                (byte) now.get(Calendar.MINUTE),
                (byte) now.get(Calendar.SECOND),
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f
        };
        btExecutor.writeCharacteristic(MiBandService.UUID_CHARACTERISTIC_DATE_TIME, time);

    }

}

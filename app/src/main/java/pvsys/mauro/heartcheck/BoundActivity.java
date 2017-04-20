package pvsys.mauro.heartcheck;


import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static android.bluetooth.le.ScanSettings.MATCH_MODE_STICKY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class BoundActivity extends AppCompatActivity {

    private final static Logger LOG = new Logger(AppCompatActivity.class.getSimpleName());


    public static final int REQUEST_ENABLE_BT = 9;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;

    private BTExecutor btExecutor;

    private Scanning isScanning = Scanning.SCANNING_OFF;
    private Button startButton;
    private TextView welcomeSub;
    private ImageView miBandImage;
    private ProgressBar circularProgress;
    private ObjectAnimator animation;

    private enum Scanning {
        SCANNING_BT,
        SCANNING_OFF
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bound);
        isScanning = Scanning.SCANNING_OFF;
        startButton = (Button) findViewById(R.id.button_start_stop_bt);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButtonClick(startButton);
            }
        });

        welcomeSub = (TextView) findViewById(R.id.welcome_sub);

        circularProgress = (ProgressBar) findViewById(R.id.progress_circular);
        animation = ObjectAnimator.ofInt (circularProgress, "progress", 0, 100); // see this max value coming back here, we animate towards that value
        animation.setDuration (3000); //in milliseconds
        animation.setInterpolator (new DecelerateInterpolator());
        animation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animation.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        if (HeartCheckApp.getPreferredDeviceAddress() != null) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }

        startBluetooth();
    }

    //The following TWO methods are addded to be able to stop discovery
    public void onStartButtonClick(View button) {
        LOG.debug("Start/Stop Button clicked");
        if (isScanning == Scanning.SCANNING_BT) {
            stopDeviceScan();
        } else {
            startBluetooth();
        }
    }

    private void stopDeviceScan() {
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        try {
            btAdapter.cancelDiscovery();
            isScanning = Scanning.SCANNING_OFF;
            startButton.setText(getString(R.string.start_scanning));
            circularProgress.clearAnimation();
        } catch (Exception exc) {
            isScanning = Scanning.SCANNING_BT;
            startButton.setText(getString(R.string.stop_scanning));
            animation.start();
        }

    }

    private void startBluetooth() {
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter != null) {
            if (!btAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            } else {
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

    private void bluetoothSetupDone() {
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            LOG.error("bluetooth not available");
            //TODO: Add behaviour
        }
        LOG.info("bluetooth available");

        //Start device scanning
        deviceScan();

    }

    private void deviceScan() {
        LOG.info("scan");
        isScanning = Scanning.SCANNING_BT; //Flag to manage start/stop button
        startButton.setText(getString(R.string.stop_scanning));

        animation.start();
        ScanCallback leScanCallback = new ScanCallback() {
            public void onScanResult(int callbackType, ScanResult result) {
                ActivityCompat.requestPermissions(BoundActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);

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
                    HeartCheckApp.setPreferredDevice(result.getDevice().getAddress());
                    //handleDeviceFound(result.getDevice()); //, (short) result.getRssi(), uuids);
                    btAdapter.getBluetoothLeScanner().stopScan(this);

                    //Once saved preferred device, we don't handle it here but we go to main activity
                    welcomeSub.setText("Discovered: "+result.getDevice().getName());
                    miBandImage = (ImageView) findViewById(R.id.image_mi_band);
                    miBandImage.setImageResource(R.mipmap.bind_mili_found);

                    circularProgress.clearAnimation();

                    startButton.setText(getString(R.string.next));
                    startButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            LOG.debug("Next Button clicked...go to main activity");
                            Intent intent = new Intent(BoundActivity.this, MainActivity.class);
                            startActivity(intent);
                        }
                    });
                } catch (NullPointerException e) {
                    LOG.warn("Error handling scan result", e);
                    btAdapter.getBluetoothLeScanner().stopScan(this);
                }
            }
        };
        List<ScanFilter> allFilters = new ArrayList<>();
        //ParcelUuid mi2Service = new ParcelUuid(MiBandService.UUID_SERVICE_MIBAND2_SERVICE);
        //allFilters.add(new ScanFilter.Builder().setServiceUuid(mi2Service).build());
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
}

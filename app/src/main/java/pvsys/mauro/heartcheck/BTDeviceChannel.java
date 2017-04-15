package pvsys.mauro.heartcheck;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BTDeviceChannel {
    private final static Logger LOG = new Logger(BTDeviceChannel.class.getSimpleName());

    public static final UUID UUID_DESCRIPTOR_GATT_CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private final BluetoothGatt gatt;
    private final Map<String, BluetoothGattCharacteristic> characteristicsMap = new HashMap<String, BluetoothGattCharacteristic>();

    public BTDeviceChannel(BluetoothGatt gatt) {
        this.gatt = gatt;
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services) {
            LOG.info("service discovered " + service.getUuid());
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic c : characteristics) {
                LOG.info("Characteristic discovered " + c.getUuid());
                characteristicsMap.put(c.getUuid().toString().toLowerCase(), c);
            }
        }
    }


    public void readCharacteristic(UUID uuid) {
        readCharacteristic(uuid.toString().toLowerCase());
    }

    public void readCharacteristic(String uuid) {
        BluetoothGattCharacteristic characteristics = this.characteristicsMap.get(uuid);
        if(!gatt.readCharacteristic(characteristics)){
            LOG.error("could not read " + characteristics.getUuid());
            //throw new RuntimeException("reading error for characteristic " + characteristics.getUuid());
        }
    }

    public void writeCharacteristic(UUID uuid, byte[] value) {
        writeCharacteristic(uuid.toString().toLowerCase(), value);
    }

    public void writeCharacteristic(String uuid, byte[] value) {
        BluetoothGattCharacteristic characteristics = this.characteristicsMap.get(uuid);
        if(!characteristics.setValue(value)){
            throw new RuntimeException("writing (setting) error for characteristic " + uuid);
        }
        if(!gatt.writeCharacteristic(characteristics)){
            LOG.error("could not write " + characteristics.getUuid());
            //throw new RuntimeException("writing error for characteristic " + uuid);
        }
    }

    public void registerToCharacteristic(UUID uuid, boolean enableFlag) {
        registerToCharacteristic(uuid.toString().toLowerCase(), enableFlag);
    }

    public void registerToCharacteristic(String uuid, boolean enableFlag) {
        BluetoothGattCharacteristic characteristics = this.characteristicsMap.get(uuid);
        if(!registerToCharacteristicOnce(characteristics, enableFlag)){
            LOG.error("could not register " + characteristics.getUuid());
            //throw new RuntimeException("registering error for characteristic " + uuid);
        }
    }

    private boolean registerToCharacteristicOnce(BluetoothGattCharacteristic characteristics, boolean enableFlag) {
        boolean result = gatt.setCharacteristicNotification(characteristics, enableFlag);
        if (result) {
            BluetoothGattDescriptor notifyDescriptor = characteristics.getDescriptor(UUID_DESCRIPTOR_GATT_CLIENT_CHARACTERISTIC_CONFIGURATION);
            if (notifyDescriptor != null) {
                int properties = characteristics.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    LOG.debug("use NOTIFICATION");
                    notifyDescriptor.setValue(enableFlag ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    result = gatt.writeDescriptor(notifyDescriptor);
                    LOG.info("ACK - descriptor written " + notifyDescriptor.getUuid() + ": " + Arrays.toString(notifyDescriptor.getValue()));
                } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    LOG.debug("use INDICATION");
                    notifyDescriptor.setValue(enableFlag ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    result = gatt.writeDescriptor(notifyDescriptor);
                    LOG.info("ACK - descriptor written " + notifyDescriptor.getUuid() + ": " + Arrays.toString(notifyDescriptor.getValue()));
                } else {
                }
            } else {
                LOG.warn("Descriptor CLIENT_CHARACTERISTIC_CONFIGURATION for characteristic " + characteristics.getUuid() + " is null");
            }
        } else {
            LOG.error("Unable to enable notification for " + characteristics.getUuid());
        }
        if(result) {
            LOG.info("Successful registration to " + characteristics.getUuid());
        } else {
            LOG.error("Error for registration to " + characteristics.getUuid());
        }
        return result;
    }
}

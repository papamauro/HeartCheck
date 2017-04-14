package pvsys.mauro.heartcheck;


import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.NonNull;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.NonNull;



import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


/**
 * A device candidate is a Bluetooth device that is not yet managed by
 * Gadgetbridge. Only if a DeviceCoordinator steps up and confirms to
 * support this candidate, will the candidate be promoted to a GBDevice.
 */
public class DeviceCandidate implements Parcelable {
    private static final Logger LOG = new Logger("DeviceCandidate");

    private final BluetoothDevice device;
    private final short rssi;
    private final ParcelUuid[] serviceUuds;
    private String deviceType = DEVICE_TYPE_UNKNOWN;

    public static final String DEVICE_TYPE_UNKNOWN = "unknown";
    public static final String DEVICE_TYPE_MIBAND = "MIBAND";
    public static final String DEVICE_TYPE_MIBAND2 = "MIBAND2";


    public DeviceCandidate(BluetoothDevice device, short rssi, ParcelUuid[] serviceUuds) {
        this.device = device;
        this.rssi = rssi;
        this.serviceUuds = mergeServiceUuids(serviceUuds, device.getUuids());
        if(this.serviceUuds != null) {
            for (ParcelUuid uuid : this.serviceUuds) {
                LOG.info("  supports uuid: " + uuid.getUuid().toString());
            }
            this.deviceType = DEVICE_TYPE_UNKNOWN;
            for (ParcelUuid uid : this.serviceUuds) {
                if (MiBandService.UUID_SERVICE_MIBAND2_SERVICE.equals(uid.getUuid())) {
                    this.deviceType = DEVICE_TYPE_MIBAND2;
                } else if (MiBandService.UUID_SERVICE_MIBAND_SERVICE.equals(uid.getUuid())) {
                    this.deviceType = DEVICE_TYPE_MIBAND;
                }
            }
        } else {
            LOG.info("NO UID supported");
        }
    }

    private DeviceCandidate(Parcel in) {
        LOG.info("READING from parcel");
        device = in.readParcelable(getClass().getClassLoader());
        if (device == null) {
            throw new IllegalStateException("Unable to read state from Parcel");
        }
        rssi = (short) in.readInt();
        deviceType = in.readString();

        ParcelUuid[] uuids = Utils.toParcelUUids(in.readParcelableArray(getClass().getClassLoader()));
        serviceUuds = mergeServiceUuids(uuids, device.getUuids());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(device, 0);
        dest.writeInt(rssi);
        dest.writeString(deviceType);
        dest.writeParcelableArray(serviceUuds, 0);
    }

    public static final Creator<DeviceCandidate> CREATOR = new Creator<DeviceCandidate>() {
        @Override
        public DeviceCandidate createFromParcel(Parcel in) {
            return new DeviceCandidate(in);
        }

        @Override
        public DeviceCandidate[] newArray(int size) {
            return new DeviceCandidate[size];
        }
    };

    public BluetoothDevice getDevice() {
        return device;
    }

    public void setDeviceType(String type) {
        deviceType = type;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getMacAddress() {
        return device != null ? device.getAddress() : "unknown";
    }

    private ParcelUuid[] mergeServiceUuids(ParcelUuid[] serviceUuds, ParcelUuid[] deviceUuids) {
        Set<ParcelUuid> uuids = new HashSet<>();
        if (serviceUuds != null) {
            uuids.addAll(Arrays.asList(serviceUuds));
        }
        if (deviceUuids != null) {
            uuids.addAll(Arrays.asList(deviceUuids));
        }
        return uuids.toArray(new ParcelUuid[0]);
    }

    @NonNull
    public ParcelUuid[] getServiceUuids() {
        return serviceUuds;
    }

    public boolean supportsService(UUID aService) {
        ParcelUuid[] uuids = getServiceUuids();
        if (uuids.length == 0) {
            LOG.warn("no cached services available for " + this);
            return false;
        }

        for (ParcelUuid uuid : uuids) {
            if (uuid != null && aService.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    public String getName() {
        String deviceName = null;
        try {
            Method method = device.getClass().getMethod("getAliasName");
            if (method != null) {
                deviceName = (String) method.invoke(device);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
            LOG.info("Could not get device alias for " + device.getName());
        }
        if (deviceName == null || deviceName.length() == 0) {
            deviceName = device.getName();
        }
        if (deviceName == null || deviceName.length() == 0) {
            deviceName = "(unknown)";
        }
        return deviceName;
    }

    public short getRssi() {
        return rssi;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeviceCandidate that = (DeviceCandidate) o;
        return device.getAddress().equals(that.device.getAddress());
    }

    @Override
    public int hashCode() {
        return device.getAddress().hashCode() ^ 37;
    }

    @Override
    public String toString() {
        return getName() + ": " + getMacAddress() + " (" + getDeviceType() + ")";
    }
}

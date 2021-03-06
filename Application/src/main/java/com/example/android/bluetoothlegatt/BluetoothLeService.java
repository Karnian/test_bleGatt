/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    protected Map<BluetoothDevice, DeviceControlActivity> mBleConnections = new HashMap<>();
    protected ArrayList<BluetoothGatt> mGattList = new ArrayList<>();
    protected ArrayList<BluetoothGattCallback> mGattCallbackList = new ArrayList<>();
    protected ArrayList<BluetoothDevice> mDevices = new ArrayList<>();
    protected ArrayList<BluetoothLeService> mLeServiceList = new ArrayList<>();

    public static final UUID THINGY_ENVIRONMENTAL_SERVICE                                       = new UUID(0xEF6802009B354933L, 0x9B1052FFA9740042L);
    public static final UUID TEMPERATURE_CHARACTERISTIC                                         = new UUID(0xEF6802019B354933L, 0x9B1052FFA9740042L);
    public static final UUID PRESSURE_CHARACTERISTIC                                            = new UUID(0xEF6802029B354933L, 0x9B1052FFA9740042L);
    public static final UUID HUMIDITY_CHARACTERISTIC                                            = new UUID(0xEF6802039B354933L, 0x9B1052FFA9740042L);

    public static final UUID SECURE_PME_SERVICE                                                 = new UUID(0xEF6804009B354933L, 0x9B1052FFA9740042L);
    public static final UUID CLASSIFICATION_CHARACTERISTIC                                      = new UUID(0xEF68040B9B354933L, 0x9B1052FFA9740042L);
    public static final UUID FEATURE_VECTOR_CHARACTERISTIC                                      = new UUID(0xEF68040C9B354933L, 0x9B1052FFA9740042L);
    public static final UUID THINGY_MOTION_CONFIGURATION_CHARACTERISTIC                         = new UUID(0xEF6804019B354933L, 0x9B1052FFA9740042L);


    public static final String TEMPERATURE_NOTIFICATION                                         = "TEMPERATURE_NOTIFICATION_";
    public static final String HUMIDITY_NOTIFICATION                                            = "HUMIDITY_NOTIFICATION_";
    public static final String EXTRA_DEVICE                                                     = "EXTRA_DEVICE";
    public static final String EXTRA_DEVICE_ADDRESS                                             = "EXTRA_DEVICE_ADDRESS";

    public static final String CLASSIFICATION_NOTIFICATION                                      = "CLASSIFICATION_NOTIFICATION_";
    public static final String FEATUREVECTOR_NOTIFICATION                                       = "FEATUREVECTOR_NOTIFICATION_";

    public static final String EXTRA_DATA_VECTORLENGTH                                          = "EXTRA_DATA_VECTORLENGTH";
    public static final String EXTRA_DATA_FEATUREVECTOR_0                                       = "EXTRA_DATA_FEATUREVECTOR_0";
    public static final String EXTRA_DATA_FEATUREVECTOR_1                                       = "EXTRA_DATA_FEATUREVECTOR_1";
    public static final String EXTRA_DATA_FEATUREVECTOR_2                                       = "EXTRA_DATA_FEATUREVECTOR_2";

    public static final String EXTRA_DATA_CLASSIFICATION_0                                       = "EXTRA_DATA_CLASSIFICATION_0";
    public static final String EXTRA_DATA_CLASSIFICATION_1                                       = "EXTRA_DATA_CLASSIFICATION_1";
    public static final String EXTRA_DATA_CLASSIFICATION_2                                       = "EXTRA_DATA_CLASSIFICATION_2";
    public static final String EXTRA_DATA_CLASSIFICATION_3                                       = "EXTRA_DATA_CLASSIFICATION_3";

    public static final String MOTION_NOTIFICATION                                            = "MOTION_NOTIFICATION_";

    public static final SimpleDateFormat TIME_FORMAT                                            = new SimpleDateFormat("HH:mm:ss:SSS");
    public static final SimpleDateFormat TIME_FORMAT_PEDOMETER                                  = new SimpleDateFormat("mm:ss:SS");

    private BluetoothGattCharacteristic mTemperatureCharacteristic;
    private BluetoothGattCharacteristic mHumidityCharacteristic;

    public BluetoothGattCharacteristic mClassificationCharacteristic;
    private BluetoothGattCharacteristic mFeatureVectorCharacteristic;
    private BluetoothGattCharacteristic mMotionConfigurationCharacteristic;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    public BluetoothDevice device;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "map created");
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction, gatt);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction, gatt);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

            final BluetoothGattService mEnvironmentService = gatt.getService(THINGY_ENVIRONMENTAL_SERVICE);
            if (mEnvironmentService != null) {
                mTemperatureCharacteristic = mEnvironmentService.getCharacteristic(TEMPERATURE_CHARACTERISTIC);
                mHumidityCharacteristic = mEnvironmentService.getCharacteristic(HUMIDITY_CHARACTERISTIC);
                Log.v(TAG, "Reading environment config chars");
            }

            final BluetoothGattService mPMEService = gatt.getService(SECURE_PME_SERVICE);
            if (mPMEService != null) {
                mClassificationCharacteristic = mPMEService.getCharacteristic(CLASSIFICATION_CHARACTERISTIC);
                mFeatureVectorCharacteristic = mPMEService.getCharacteristic(FEATURE_VECTOR_CHARACTERISTIC);
                mMotionConfigurationCharacteristic = mPMEService.getCharacteristic(THINGY_MOTION_CONFIGURATION_CHARACTERISTIC);
                Log.v(TAG, "Reading PME char " + gatt.getDevice().getAddress());
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "Checking PME char");
                if(characteristic.equals(mTemperatureCharacteristic))
                    broadcastUpdate(TEMPERATURE_NOTIFICATION, characteristic, gatt);
                if(characteristic.equals(mHumidityCharacteristic))
                    broadcastUpdate(HUMIDITY_NOTIFICATION, characteristic, gatt);
                if(characteristic.equals(mClassificationCharacteristic))
                    broadcastUpdate(CLASSIFICATION_NOTIFICATION, characteristic, gatt);
                if(characteristic.equals(mFeatureVectorCharacteristic))
                    broadcastUpdate(FEATUREVECTOR_NOTIFICATION, characteristic, gatt);
                if(characteristic.equals(mMotionConfigurationCharacteristic))
                    broadcastUpdate(MOTION_NOTIFICATION, characteristic, gatt);
//                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
//            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            Log.v(TAG, gatt.getDevice().getAddress() + " // CHR changed");
            if (characteristic.equals(mTemperatureCharacteristic)) {
                broadcastUpdate(TEMPERATURE_NOTIFICATION, mTemperatureCharacteristic, gatt);
            } else if (characteristic.equals(mHumidityCharacteristic)) {
                broadcastUpdate(HUMIDITY_NOTIFICATION, mHumidityCharacteristic, gatt);
            } else if (characteristic.equals(mClassificationCharacteristic)) {
                broadcastUpdate(CLASSIFICATION_NOTIFICATION, mClassificationCharacteristic, gatt);
            } else if (characteristic.equals(mFeatureVectorCharacteristic)) {
                broadcastUpdate(FEATUREVECTOR_NOTIFICATION, mFeatureVectorCharacteristic, gatt);
            } else if (characteristic.equals(mMotionConfigurationCharacteristic)) {
                broadcastUpdate(MOTION_NOTIFICATION, mMotionConfigurationCharacteristic, gatt);
            }
        }
    };

    private void broadcastUpdate(final String action, BluetoothGatt gatt) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DEVICE_ADDRESS, gatt.getDevice().getAddress());
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DEVICE, String.valueOf(gatt.getDevice().getAddress()));
        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (CLASSIFICATION_NOTIFICATION.equals(action)) {
            Log.d(TAG, "Classification update");
            final int mClassificationInt1 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
            final int mClassificationInt2 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 1);
            final int mClassificationInt3 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 2);
            final int mClassificationInt4 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 3);

//            intent.putExtra(EXTRA_DEVICE, mBluetoothDevice);
            intent.putExtra(EXTRA_DATA_CLASSIFICATION_0, String.valueOf(mClassificationInt1));
            intent.putExtra(EXTRA_DATA_CLASSIFICATION_1, String.valueOf(mClassificationInt2));
            intent.putExtra(EXTRA_DATA_CLASSIFICATION_2, String.valueOf(mClassificationInt3));
            intent.putExtra(EXTRA_DATA_CLASSIFICATION_3, String.valueOf(mClassificationInt4));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);

        mBleConnections.put(device, new DeviceControlActivity());
        if (!mDevices.contains(device)) {
            mDevices.add(device);
            mGattList.add(mBluetoothGatt);
            mGattCallbackList.add(mGattCallback);
            mLeServiceList.add(this);
        }

        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "initialize character : " + characteristic.getUuid());
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        UUID charUuid = characteristic.getUuid();
        if(TEMPERATURE_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Temperature initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } else if(HUMIDITY_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Humidity initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } else if(CLASSIFICATION_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Classification initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } else if(FEATURE_VECTOR_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "FeatureVector initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public void setCharacteristicNotification(BluetoothGatt bleGatt, BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "initialize character : " + characteristic.getUuid());
        bleGatt.setCharacteristicNotification(characteristic, enabled);
        UUID charUuid = characteristic.getUuid();
        if(TEMPERATURE_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Temperature initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bleGatt.writeDescriptor(descriptor);
        } else if(HUMIDITY_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Humidity initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bleGatt.writeDescriptor(descriptor);
        } else if(CLASSIFICATION_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "Classification initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bleGatt.writeDescriptor(descriptor);
        } else if(FEATURE_VECTOR_CHARACTERISTIC.equals(charUuid)) {
            Log.d(TAG, "FeatureVector initialized");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIGURATOIN_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bleGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public void changeCharacteristic(BluetoothDevice device, boolean enable, BluetoothGatt bleGatt) {
        final BluetoothGattService cPMEService = bleGatt.getService(SECURE_PME_SERVICE);
        if (cPMEService != null) {
            BluetoothGattCharacteristic rClassificationCharacteristic = cPMEService.getCharacteristic(CLASSIFICATION_CHARACTERISTIC);
            setCharacteristicNotification(rClassificationCharacteristic, enable);
            mGattCallback.onCharacteristicChanged(bleGatt, rClassificationCharacteristic);
            Log.d(TAG, device.getAddress() + "// " + rClassificationCharacteristic + enable);
        }
    }
}

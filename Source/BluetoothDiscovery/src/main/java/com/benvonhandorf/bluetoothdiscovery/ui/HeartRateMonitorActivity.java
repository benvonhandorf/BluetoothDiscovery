package com.benvonhandorf.bluetoothdiscovery.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import butterknife.InjectView;
import butterknife.Views;
import com.benvonhandorf.bluetoothdiscovery.BluetoothDeviceWrapper.Characteristic;
import com.benvonhandorf.bluetoothdiscovery.BluetoothDeviceWrapper.Device;
import com.benvonhandorf.bluetoothdiscovery.HeartRateMonitorWrapper.HRMSensor.BodySensorLocationCharacteristic;
import com.benvonhandorf.bluetoothdiscovery.HeartRateMonitorWrapper.HRMSensor.HeartRateCharacteristic;
import com.benvonhandorf.bluetoothdiscovery.HeartRateMonitorWrapper.PolarH6HRMDevice;
import com.benvonhandorf.bluetoothdiscovery.R;

/**
 * Created by benvh on 9/28/13.
 */
public class HeartRateMonitorActivity extends Activity implements Device.OnDeviceStateChangedListener {
    private static final String TAG = HeartRateMonitorActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 55555;
    private PolarH6HRMDevice _device;

    @InjectView(R.id.text_value)
    TextView _reading;
    private boolean _inFocus = false;
    private Handler _uiThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _uiThreadHandler = new Handler();

        setContentView(R.layout.activity_simple_data);

        Views.inject(this);

    }

    @Override
    protected void onResume() {
        super.onResume();

        _inFocus = true;

        _reading.setText(String.format("Disconnected"));

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            _device = new PolarH6HRMDevice(this, bluetoothAdapter);

            _device.setDeviceReadyListener(this);

            _device.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        _inFocus = false;

        _device.disconnect();
    }

    @Override
    public void onDeviceReady(Device device) {
        Log.v(TAG, "Device ready");

        _uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                _reading.setText(String.format("Connected"));
            }
        });

        //Enable the IR Sensor
        _device.getHRMService()
                .getHeartRateCharacteristic()
                .setCharacteristicListener(new Characteristic.CharacteristicListener() {
                    @Override
                    public void onValueChanged(Characteristic characteristic) {
                        HeartRateCharacteristic heartRateCharacteristic = (HeartRateCharacteristic) characteristic;

                        int bpmReading = heartRateCharacteristic.getBPM();

                        String reading = String.format("%d bpm", bpmReading);

                        setReading(reading);
                    }
                }

                );

        _device.getHRMService()
                .getBodySensorLocationCharacteristic()

                .setCharacteristicListener(new Characteristic.CharacteristicListener() {
                    @Override
                    public void onValueChanged(Characteristic characteristic) {
                        BodySensorLocationCharacteristic bodySensorLocationCharacteristic = (BodySensorLocationCharacteristic) characteristic;

                        Log.v(TAG, String.format("Body Location Changed: %d"
                                , bodySensorLocationCharacteristic.getLocation()));
                    }
                }

                );

        _device.getHRMService().getBodySensorLocationCharacteristic()
                .read();

        _device.getHRMService().
                getHeartRateCharacteristic()
                .enableNotification();
    }

    private void setReading(final String reading) {
        _uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                _reading.setText(reading);

                Log.v(TAG, String.format("Heart Rate Reading: %s"
                        , reading));
            }
        });
    }

    @Override
    public void onDeviceDisconnect(Device device) {
        setReading("Disconnected");

        Log.v(TAG, "Device disconnected");
        if (_inFocus) {
            //Attempt to reconnect
            Log.v(TAG, "Attempting reconnect");
            device.connect();
        }
    }
}
